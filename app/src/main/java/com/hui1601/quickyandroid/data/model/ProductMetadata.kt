package com.hui1601.quickyandroid.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductColor(
    val name: String = "",
    val color: String = "",
    @SerialName("colorIndex")
    val colorIndex: Int = 0,
    @SerialName("iconUrl")
    val iconUrl: String = "",
    @SerialName("leftImgUrl")
    val leftImgUrl: String = "",
    @SerialName("rightImgUrl")
    val rightImgUrl: String = "",
    @SerialName("boxImgUrl")
    val boxImgUrl: String = "",
    @SerialName("animationImgZip")
    val animationImgZip: String = ""
)

@Serializable
data class ProductMetadata(
    @SerialName("vendorId")
    val vendorId: Int,
    val title: String,
    @SerialName("subTitle")
    val subTitle: String = "",
    val category: String = "earphones",
    @SerialName("vendorType")
    val vendorType: String = "standard",
    @SerialName("modelId")
    val modelId: Int? = null,
    @SerialName("iconUrl")
    val iconUrl: String = "",
    @SerialName("leftImgUrl")
    val leftImgUrl: String = "",
    @SerialName("rightImgUrl")
    val rightImgUrl: String = "",
    @SerialName("boxImgUrl")
    val boxImgUrl: String = "",
    @SerialName("animationColor")
    val animationColor: String = "",
    @SerialName("animationImgZip")
    val animationImgZip: String = "",
    val colors: List<ProductColor> = emptyList(),
    val features: ProductFeatures? = null
)

@Serializable
data class ProductFeatures(
    val anc: AncFeature? = null,
    val eq: EqFeature? = null,
    @SerialName("key_function")
    val keyFunction: KeyFunctionFeature? = null,
    @SerialName("channel_balance")
    val channelBalance: Boolean = false,
    @SerialName("find_earphone")
    val findEarphone: Boolean = false,
    @SerialName("device_name")
    val deviceName: Boolean = false,
    @SerialName("auto_off_timer")
    val autoOffTimer: AutoOffTimer? = null,
    val settings: List<SettingItem>? = null
)

@Serializable
data class AncFeature(
    val modes: List<AncMode> = emptyList()
)

@Serializable
data class AncMode(
    val name: String = "",
    @SerialName("startcmdid")
    val startCmdId: Int? = null,
    @SerialName("endcmdid")
    val endCmdId: Int? = null,
    @SerialName("defaultcmd")
    val defaultCmd: Int? = null,
    @SerialName("viewtype")
    val viewType: Int? = null,
    val items: List<AncItem>? = null
)

@Serializable
data class AncItem(
    val name: String = "",
    @SerialName("startcmdid")
    val startCmdId: Int? = null,
    @SerialName("endcmdid")
    val endCmdId: Int? = null
)

@Serializable
data class EqFeature(
    val bands: Int = 10,
    @SerialName("mindb")
    val minDb: Int = 8,
    @SerialName("maxdb")
    val maxDb: Int = 8,
    val freq: String = "",
    val characteristic: String = "",
    val presets: List<String> = emptyList()
)

@Serializable
data class KeyFunctionFeature(
    val events: List<KeyEvent> = emptyList()
)

@Serializable
data class KeyEvent(
    val name: String = "",
    val functions: List<String> = emptyList()
)

@Serializable
data class AutoOffTimer(
    @SerialName("cmdid")
    val cmdId: Int? = null,
    val repeat: Int = 0
)

@Serializable
data class SettingItem(
    val name: String = "",
    val type: String = "",
    @SerialName("cmdid")
    val cmdId: Int? = null,
    val cmd: String? = null
)
