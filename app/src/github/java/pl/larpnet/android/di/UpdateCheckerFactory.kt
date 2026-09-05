package pl.larpnet.android.di

import android.content.Context
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.UpdateChecker
import pl.larpnet.android.data.repository.UpdateRepository
import pl.larpnet.android.network.GitHubApi

fun createUpdateChecker(context: Context, gitHubApi: GitHubApi, tokenStore: TokenStore): UpdateChecker =
    UpdateRepository(gitHubApi, tokenStore)
