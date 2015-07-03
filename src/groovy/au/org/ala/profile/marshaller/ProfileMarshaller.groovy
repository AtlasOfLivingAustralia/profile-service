package au.org.ala.profile.marshaller

import au.org.ala.profile.Profile
import grails.converters.JSON

class ProfileMarshaller {

    void register() {
        JSON.registerObjectMarshaller(Profile) { Profile profile ->
            return [
                    uuid             : profile.uuid,
                    guid             : profile.guid && profile.guid != "null" ? "${profile.guid}" : "",
                    nslNameIdentifier: profile.nslNameIdentifier,
                    dataResourceUid  : profile.opus?.dataResourceUid,
                    opusId           : profile.opus?.uuid,
                    opusName         : profile.opus?.title,
                    privateMode      : profile.privateMode,
                    rank             : profile.rank,
                    scientificName   : profile.scientificName,
                    nameAuthor       : profile.nameAuthor,
                    fullName         : profile.fullName,
                    matchedName      : profile.matchedName ? [scientificName: profile.matchedName.scientificName,
                                                              nameAuthor    : profile.matchedName.nameAuthor,
                                                              fullName      : profile.matchedName.fullName] : null,
                    classification   : profile.classification,
                    attributes       : profile.attributes?.sort(),
                    links            : profile.links,
                    bhl              : profile.bhlLinks,
                    primaryImage     : profile.primaryImage,
                    excludedImages   : profile.excludedImages ?: [],
                    specimenIds      : profile.specimenIds ?: [],
                    authorship       : profile.authorship?.collect { [category: it.category.name, text: it.text] },
                    bibliography     : profile.bibliography?.collect {
                        [uuid: it.uuid, text: it.text, order: it.order]
                    }?.sort { it.order },
                    createdDate      : profile.dateCreated,
                    createdBy        : profile.createdBy,
                    lastUpdated      : profile.lastUpdated,
                    lastUpdatedBy    : profile.lastUpdatedBy
            ]
        }
    }
}
