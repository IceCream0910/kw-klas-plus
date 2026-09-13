package com.icecream.kwklasplus.core.academic

import android.content.Context
import android.util.AtomicFile
import java.io.File

class AndroidAcademicWidgetSnapshotStore(context: Context) : AcademicWidgetSnapshotStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "academic_widget_snapshot_v1.json"))

    @Synchronized
    override fun read(): AcademicWidgetSnapshot? = try {
        AcademicWidgetSnapshotCodec.decode(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
    } catch (_: Exception) { null }

    @Synchronized
    override fun write(snapshot: AcademicWidgetSnapshot) {
        val stream = file.startWrite()
        try {
            stream.write(AcademicWidgetSnapshotCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (cause: Exception) {
            file.failWrite(stream)
            throw cause
        }
    }

    @Synchronized
    override fun clear() = file.delete()
}
