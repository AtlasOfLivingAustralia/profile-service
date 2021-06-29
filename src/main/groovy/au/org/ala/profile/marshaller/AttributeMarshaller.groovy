package au.org.ala.profile.marshaller

import au.org.ala.profile.Attribute
import au.org.ala.profile.Opus
import au.org.ala.profile.Profile
import au.org.ala.profile.util.Utils
import grails.converters.JSON

class AttributeMarshaller {

    void register() {
        JSON.registerObjectMarshaller(Attribute) { Attribute attr ->
            return [
                    uuid        : attr.uuid,
                    title       : attr.title.name,
                    order       : attr.title.order,
                    required    : attr.title.required,
                    containsName: attr.title.containsName,
                    summary     : attr.title.summary,
                    text        : attr.text,
                    source      : attr.source,
                    plainText   : Utils.cleanupText(attr.text),
                    creators    : attr.creators?.collect { it.name },
                    editors     : attr.editors?.collect { it.name },
                    original    : attr.original,
                    profile     : attr.profile ? marshalProfile(attr.profile) : null
            ]
        }
    }

    def marshalProfile(Profile profile) {
        return [
                uuid          : profile.uuid,
                scientificName: profile.scientificName,
                opus          : marshalOpus(profile.opus)
        ]
    }

    def marshalOpus(Opus opus) {
        return [
                uuid     : opus.uuid,
                title    : opus.title,
                shortName: opus.shortName
        ]
    }


}