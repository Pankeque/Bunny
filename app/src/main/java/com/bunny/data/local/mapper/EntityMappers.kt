package com.bunny.data.local.mapper

import com.bunny.data.local.entity.UserEntity
import com.bunny.domain.model.User

fun UserEntity.toDomain() = User(id = id, username = username, avatarUrl = avatarUrl, theme = theme)
