// PsyZine, 2026-10-11. GPL-3.0-or-later.
package com.isaakhanimann.journal.data.attachments
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton
@Singleton class JournalMutationLock @Inject constructor() { val mutex = Mutex() }
