package com.notes.app.data.db

import androidx.room.TypeConverter
import com.notes.app.data.model.NoteType

class TypeConverters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)
}
