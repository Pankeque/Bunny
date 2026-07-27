package com.bunny.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunny.data.remote.socket.SocketService
import com.bunny.domain.repository.AuthRepository
import com.bunny.domain.repository.UserRepository
import com.bunny.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val socketService: SocketService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(username: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(username, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
            result.onSuccess { response ->
                socketService.connect(response.accessToken)
                onResult(Result.success(response.accessToken))
            }.onFailure { e ->
                onResult(Result.failure(e))
            }
        }
    }

    fun register(username: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(username, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
            result.onSuccess { response ->
                socketService.connect(response.accessToken)
                onResult(Result.success(response.accessToken))
            }.onFailure { e ->
                onResult(Result.failure(e))
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            socketService.disconnect()
            authRepository.logout()
            onComplete()
        }
    }

    fun updateProfile(username: String?, avatarUrl: String?, theme: String?, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.updateProfile(username, avatarUrl, theme)
            onResult(result.map { })
        }
    }
}
