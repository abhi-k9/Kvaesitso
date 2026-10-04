package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileBadgeProvider : BadgeProvider, KoinComponent {
    private val profileManager: ProfileManager by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> = flow {
        val userHandle = when (searchable) {
            is Application -> searchable.user
            is AppShortcut -> searchable.user
            else -> null
        }
        if (userHandle != null) {
            emitAll(
                combine(
                    profileManager.getProfileByUserHandle(userHandle),
                    profileManager.additionalProfiles,
                ) { profile, additionalProfiles ->
                    when {
                        profile?.type == Profile.Type.Work -> WorkProfile
                        profile?.type == Profile.Type.Private -> PrivateProfile
                        // e.g. clone profiles ("dual apps"), to tell the clones from the originals
                        additionalProfiles.any { it.userHandle == userHandle } -> AdditionalProfile
                        else -> null
                    }
                }
            )
        } else {
            emit(null)
        }
    }

    companion object {
        private val WorkProfile = Badge(
            icon = BadgeIcon(R.drawable.enterprise_20px)
        )

        private val PrivateProfile = Badge(
            icon = BadgeIcon(R.drawable.encrypted_20px)
        )

        private val AdditionalProfile = Badge(
            icon = BadgeIcon(R.drawable.content_copy_24px)
        )
    }
}