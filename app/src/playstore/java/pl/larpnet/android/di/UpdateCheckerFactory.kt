package pl.larpnet.android.di

import android.content.Context
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.PlayUpdateRepository
import pl.larpnet.android.data.repository.UpdateChecker
import pl.larpnet.android.network.GitHubApi

fun createUpdateChecker(context: Context, @Suppress("UNUSED_PARAMETER") gitHubApi: GitHubApi, tokenStore: TokenStore): UpdateChecker =
    PlayUpdateRepository(context, tokenStore)
