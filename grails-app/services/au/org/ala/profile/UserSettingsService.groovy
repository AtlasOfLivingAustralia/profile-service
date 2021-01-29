package au.org.ala.profile

import grails.gorm.transactions.Transactional

@Transactional
class UserSettingsService extends BaseDataAccessService {

    /**
     * Gets the UserSettings for a given user id, inserting a new one if there was no existing user settings.
     * @param userId The user id
     * @return The user settings
     */
    UserSettings getUserSettings(String userId) {
        checkArgument userId
        def settings = UserSettings.get(userId)
        if (!settings) {
            def newSettings = new UserSettings(id: userId)
            newSettings.id = userId
            settings = newSettings.insert(validate: true, failOnError: true, flush: true)
        }
        return settings
    }

    /**
     * Set the florula list for the given user, opus
     * @param userId The user id
     * @param opusUuid The opus id
     * @param listId The list drUid
     */
    void setFlorulaList(UserSettings userSettings, String opusUuid, String listId) {
                def user = UserSettings.findById('1234')
                user.test = 'a;'
                user.enableFlorulaList('0ded7a77-9efb-4684-8df0-48cbb1933684', 'dr12')
//                user.allFlorulaSettings = null
                userSettings.markDirty('allFlorulaSettings')
                user.save(flush: true)

////        userSettings.discard()
//        UserSettings.withNewSession {
////            userSettings.attach()
//            userSettings = UserSettings.findById('123')
//            userSettings.allFlorulaSettings.get('0ded7a77-9efb-4684-8df0-48cbb1933684').drUid = 'rrrr'
////            checkArgument userSettings
////            userSettings.enableFlorulaList(opusUuid, listId)
//            userSettings.markDirty('allFlorulaSettings')
//            userSettings.save(validate: true, failOnError: true, flush: true)
//        }
    }
}
