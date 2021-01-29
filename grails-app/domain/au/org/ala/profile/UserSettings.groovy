package au.org.ala.profile

class UserSettings {

    // user id
    String id

    // Opus.uuid -> FlorulaSettings
    Map<String, FlorulaSettings> allFlorulaSettings = [:]

    Date dateCreated
    Date lastUpdated

    void enableFlorulaList(String opusUuid, String listId) {
        def florulaSettings = allFlorulaSettings[opusUuid]
        if (!florulaSettings) {
            florulaSettings = new FlorulaSettings()
            allFlorulaSettings[opusUuid] = florulaSettings
        }
        florulaSettings.drUid = listId
        florulaSettings.markDirty('druid')
    }

    static embedded = [ 'allFlorulaSettings' ]

    static mapping = {
        id generator: 'assigned', unique: true
    }
}
