// The Whisper model as an install-time Play Asset Delivery pack (ADR 0004). Play installs it with
// the app, so the app still never downloads anything itself. scripts/fetch-model.sh fills
// src/main/assets/model/ (gitignored).
plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("model_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
