package com.smartwash.feature.mood.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.AppLogger
import com.smartwash.common.utils.model.RequestState
import com.smartwash.feature.mood.MoodConstant
import com.smartwash.feature.mood.MoodMonth
import com.smartwash.feature.mood.R
import com.smartwash.feature.mood.network.SaveMoodRequest
import com.smartwash.feature.mood.repository.MoodRepository
import com.smartwash.feature.mood.ui.moodSnapshotHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 今日记录 ViewModel：pixy 式低负担——点色块（level）即保存；词/一句话为可选增强，
 * 当日已有 level 时变更即保存（upsert 覆盖），无 level 时仅停留为本地草稿。
 *
 * 回填：进入页面拉当月月历（契约无「查今日」端点），命中今天的记录则回填 level/word
 * （月历契约不含 note，备注不回填）；保存成功后用返回的当日记录整体回填，与服务器对齐。
 */
@HiltViewModel
class MoodTodayViewModel @Inject constructor(
    private val repository: MoodRepository,
) : ViewModel() {

    /**
     * 表单草稿。[hasRecord] 表示服务器当前已有当日记录（决定词/备注变更是否即时保存）。
     */
    data class Draft(
        val level: Int? = null,
        val word: String? = null,
        val note: String = "",
        val hasRecord: Boolean = false,
    )

    private val _draft = MutableStateFlow(Draft())
    val draft = _draft.asStateFlow()

    private val _loadState = MutableStateFlow<RequestState>(RequestState.Idle)
    val loadState = _loadState.asStateFlow()

    private val _saveState = MutableStateFlow<RequestState>(RequestState.Idle)
    val saveState = _saveState.asStateFlow()

    init {
        load()
    }

    /** 拉当月月历并回填今日记录（失败可重试，草稿不受影响） */
    fun load(today: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _loadState.value = RequestState.Loading
            try {
                val todayKey = today.toString()
                val record = repository.monthCalendar(MoodMonth.of(today)).find { it.date == todayKey }
                _draft.update {
                    if (record != null) {
                        // 月历契约无 note，备注不回填；只回填 level/word
                        it.copy(level = record.level, word = record.word, hasRecord = true)
                    } else {
                        it.copy(hasRecord = false)
                    }
                }
                _loadState.value = RequestState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MoodTodayViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "MoodTodayViewModel.load: ${e.message}", e)
                _loadState.value = RequestState.Error(R.string.mood_error_load)
            }
        }
    }

    /**
     * 点心情色块：一次点按即存（低负担底线）。重复点同一色块仍提交——覆盖语义对重复点击幂等。
     */
    fun selectLevel(level: Int) {
        if (level !in MoodConstant.LEVEL_MIN..MoodConstant.LEVEL_MAX) return
        _draft.update { it.copy(level = level) }
        save()
    }

    /** 点词 chip：再点一次取消；当日已有 level 时即时保存，否则停留为草稿 */
    fun toggleWord(word: String) {
        _draft.update {
            val next = if (it.word == word) null else word
            it.copy(word = next?.take(MoodConstant.WORD_MAX_LENGTH))
        }
        if (_draft.value.level != null) save()
    }

    /** 备注输入（只更新草稿，提交见 [commitNote]） */
    fun updateNote(text: String) {
        _draft.update { it.copy(note = text.take(MoodConstant.NOTE_MAX_LENGTH)) }
    }

    /** 备注提交（键盘「完成」触发）：当日已有 level 时即时保存 */
    fun commitNote() {
        if (_draft.value.level != null) save()
    }

    /**
     * 保存（upsert）。color 快照取词典基准 hex（契约：快照决定历史色）；word/note 空白按 null 存。
     * 成功后以服务端返回整体回填草稿（含 trim 后的 word/note），与服务器对齐。
     */
    fun save() {
        val current = _draft.value
        val level = current.level ?: return
        viewModelScope.launch {
            _saveState.value = RequestState.Loading
            try {
                val vo = repository.saveToday(
                    SaveMoodRequest(
                        level = level,
                        color = moodSnapshotHex(level),
                        word = current.word?.takeIf { it.isNotBlank() },
                        note = current.note.trim().takeIf { it.isNotBlank() },
                    )
                )
                _draft.update {
                    it.copy(
                        level = vo.level,
                        word = vo.word,
                        note = vo.note.orEmpty(),
                        hasRecord = true,
                    )
                }
                _saveState.value = RequestState.Success
                AppLogger.info(TAG, "心情记录保存成功 level=$level（${if (current.hasRecord) "覆盖" else "新建"}）")
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                AppLogger.error(TAG, "MoodTodayViewModel.save: ${e.message}", e)
                _saveState.value = RequestState.Error(e.resId, e.message)
            } catch (e: Exception) {
                AppLogger.error(TAG, "MoodTodayViewModel.save: ${e.message}", e)
                _saveState.value = RequestState.Error(R.string.mood_error_save)
            }
        }
    }

    /** 保存反馈消费后复位（避免返回页面重复触发） */
    fun resetSaveState() {
        _saveState.value = RequestState.Idle
    }

    private companion object {
        const val TAG = "MoodTodayViewModel"
    }
}
