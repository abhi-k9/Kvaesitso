package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.SearchableKeyMigrator
import org.koin.core.qualifier.named
import org.koin.dsl.module

val customAttrsModule = module {
    factory<Backupable>(named<CustomAttributesRepository>()) {
        CustomAttributesRepositoryImpl(get(), get(), get(), get<SearchUiSettings>().keyboardLayout)
    }
    factory<CustomAttributesRepository> {
        CustomAttributesRepositoryImpl(get(), get(), get(), get<SearchUiSettings>().keyboardLayout)
    }
    single<SearchableKeyMigrator> { SearchableKeyMigratorImpl(get(), get()) }
    single<TagFoldersRepository> { TagFoldersRepositoryImpl(get(), get(), get()) }
}
