package app.yatori.android

import mobileapi.Engine

object EngineRegistry {
    @Volatile
    var engine: Engine? = null
}
