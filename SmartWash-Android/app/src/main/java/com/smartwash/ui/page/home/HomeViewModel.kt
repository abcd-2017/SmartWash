package com.smartwash.ui.page.home;

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.api.UserApi
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userApi: UserApi
) : ViewModel() {
    private val _hasUserSchool = MutableStateFlow<Long>(-1)
    val hasUserSchool = _hasUserSchool.asStateFlow()
    private val _getSchoolState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getSchoolState = _getSchoolState.asStateFlow()

    fun getUserSchool() {
        _getSchoolState.value = RequestState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = userApi.getUserSchoolId()
                withContext(Dispatchers.Main) {
                    _hasUserSchool.value = res.data ?: -1
                    _getSchoolState.value = RequestState.Success
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _getSchoolState.value = RequestState.Error("$e")
                }
            }
        }
    }
}
