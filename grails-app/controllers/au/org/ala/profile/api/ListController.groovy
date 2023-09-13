package au.org.ala.profile.api


import au.org.ala.profile.BaseController
import au.org.ala.profile.CollectionList
import au.org.ala.profile.Opus

class ListController extends BaseController{
    def listCollections() {
        def opuses = Opus.findAll()
        List filtered = opuses.findAll(it-> !it.privateCollection)
                .collect{new CollectionList(uuid: it.uuid, shortName:it.shortName, title:it.title, thumbnailUrl:it.thumbnailUrl, description:it.description)}

        respond filtered
    }
}
