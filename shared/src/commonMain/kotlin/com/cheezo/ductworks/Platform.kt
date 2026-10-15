package com.cheezo.ductworks

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform