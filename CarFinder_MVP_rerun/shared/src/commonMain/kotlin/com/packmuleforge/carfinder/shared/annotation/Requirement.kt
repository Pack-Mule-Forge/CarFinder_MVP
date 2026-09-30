package com.packmuleforge.carfinder.shared.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class Requirement(vararg val ids: String)
