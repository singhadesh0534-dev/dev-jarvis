package com.adesh.devjarvis.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.cloud.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(private val account: AccountManager) : ViewModel() {
    val state: StateFlow<AccountManager.State> = account.state
    val available: Boolean get() = account.available
    val googleOn: StateFlow<Boolean> = account.googleOn
    val otpOn: StateFlow<Boolean> = account.otpOn

    init { viewModelScope.launch { account.checkProviders() } }

    fun google() = account.startGoogle()
    fun logIn(email: String, password: String) = viewModelScope.launch { account.logIn(email, password) }
    fun signUp(email: String, password: String) = viewModelScope.launch { account.signUp(email, password) }
    fun forgot(email: String) = viewModelScope.launch { account.resetPassword(email) }
    fun sendCode(email: String, password: String?, signup: Boolean) = viewModelScope.launch { account.sendCode(email, password, signup) }
    fun verifyCode(code: String) = viewModelScope.launch { account.verifyCode(code) }
    fun cancelCode() = account.cancelCode()
    fun logOut() = viewModelScope.launch { account.logOut() }
    fun syncNow() = viewModelScope.launch { account.push() }
    fun skip() { account.asked = true }
}
