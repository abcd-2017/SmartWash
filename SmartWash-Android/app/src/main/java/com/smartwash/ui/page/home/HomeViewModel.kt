package com.smartwash.ui.page.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartwash.network.exception.NetworkException
import com.smartwash.utils.AppConstant
import com.smartwash.R
import com.smartwash.repository.UserRepository
import com.smartwash.utils.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _userSchoolId = MutableStateFlow<Long>(-1)
    val hasUserSchool = _userSchoolId.asStateFlow()
    private val _getSchoolState = MutableStateFlow<RequestState>(RequestState.Idle)
    val getSchoolState = _getSchoolState.asStateFlow()

    fun getUserSchool() {
        _getSchoolState.value = RequestState.Loading

        viewModelScope.launch {
            try {
                _userSchoolId.value = userRepository.getUserSchoolId()
                _getSchoolState.value = RequestState.Success
            } catch (e: NetworkException) {
                Log.e(AppConstant.APP_NAME, "HomeViewModel.getUserSchool: ${e.message}", e)
                _getSchoolState.value = RequestState.Error(e.resId, e.message)
            }
        }
    }
}
