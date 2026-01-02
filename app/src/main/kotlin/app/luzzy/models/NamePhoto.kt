package app.luzzy.models

data class NamePhoto(val name: String, val photoUri: String?, val company: String = "", val jobPosition: String = "", val isContact: Boolean = false)
