package com.gapiyka.kyivwalker

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Point(
    val lat: Double,
    val long: Double,
) : Parcelable