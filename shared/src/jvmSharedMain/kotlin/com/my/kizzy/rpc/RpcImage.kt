/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcImage.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.rpc

/**
 * Modified by Zion Huang
 */
sealed class RpcImage {
    abstract suspend fun resolveImage(resolveExternalImage: suspend (String) -> String?): String?

    class ExternalImage(
        val image: String,
        private val fallbackDiscordAsset: String? = null,
    ) : RpcImage() {
        override suspend fun resolveImage(resolveExternalImage: suspend (String) -> String?): String? {
            val asset = ArtworkCache.getOrFetch(image) { resolveExternalImage(image) }
            return when {
                asset != null -> if (asset.startsWith("http") || asset.startsWith("mp:")) asset else "mp:$asset"
                image.startsWith("http") -> image // Raw URL
                else -> fallbackDiscordAsset?.let { if (it.startsWith("http")) it else "mp:$it" }
            }
        }
    }
}
