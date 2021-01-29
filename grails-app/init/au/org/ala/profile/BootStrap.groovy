package au.org.ala.profile

import au.org.ala.profile.Profile
import au.org.ala.profile.listener.AuditListener
import au.org.ala.profile.listener.LastUpdateListener
import au.org.ala.profile.listener.ValueConverterListener
import au.org.ala.profile.sanitizer.SanitizedHtml
import com.mongodb.BasicDBList
import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import org.grails.datastore.mapping.core.Datastore
import org.springframework.web.context.WebApplicationContext

class BootStrap {

    def auditService
    def authService
    def grailsApplication
    MongoClient mongo
    def sanitizerPolicy

    def init = { servletContext ->

        def ctx = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)
        initDatastores(ctx)

        ctx.getBean("customObjectMarshallers").register()

        createDefaultTags()

        fixMultimedia()

        addStatusToProfiles()

        addTimestampToOpera()

        addScientificNameLowerToProfiles()
    }
    def destroy = {
    }

    void createDefaultTags() {
        if (Tag.count() == 0) {
            Tag iek = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "IEK", name: "Indigenous Ecological Knowledge", colour: "#c7311c")
            iek.save(flush: true)

            Tag flora = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "FLORA", name: "Flora Treatments", colour: "#2ac71c")
            flora.save(flush: true)

            Tag fauna = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "FAUNA", name: "Fauna Treatments", colour: "#8d968c")
            fauna.save(flush: true)
        }
    }


    // Add custom GORM event listeners
    def initDatastores(ctx) {
        ctx.getBeansOfType(Datastore).values().each { Datastore d ->
            log.info "Adding listener for datastore: ${d}"
            ctx.addApplicationListener new AuditListener(d, auditService)
            ctx.addApplicationListener new LastUpdateListener(d, authService)
            ctx.addApplicationListener(ValueConverterListener.of(d, SanitizedHtml, String, sanitizerPolicy.&sanitizeField))
        }
    }


    // TODO Remove this when unnecessary
    def fixMultimedia() {
        if ((grailsApplication.config.multimedia.fix.enabled ?: '')?.toBoolean()) {
            log.info("Fixing multimedia")
            try {
                MongoCollection<BasicDBObject> myColl = mongo.getDatabase(grailsApplication.config.grails.mongodb.databaseName).getCollection("profile", BasicDBObject.class)
                def q = new BasicDBObject('documents', new BasicDBObject('$exists', true));
                final cursor = myColl.find(q)
                // final count = cursor.size()
                cursor.each { DBObject profile ->
                    def changed = false
                    profile.documents.each { DBObject doc ->
                        if (!doc.url) {
                            def embed = doc.embeddedVideo ?: doc.embeddedAudio
                            def isVideo = doc.embeddedVideo != null
                            def matches = (embed =~ /src="(.*?)"/)
                            try {
                                def url = matches[0][1]
                                // This works for YouTube but not SoundCloud but at the time for writing there were only YouTube URLs in dev and none in prod.
                                doc.url = url

                                if (isVideo) {
                                    doc.type = 'video'
                                } else {
                                    doc.type = 'audio'
                                }
                                log.info("Updating ${profile.scientificName} ${doc.documentId} from ${embed} to url ${doc.url} as type ${doc.type}")
                                changed = true
                            } catch (e) {
                                log.error("Couldn't find a url in $embed in ${doc.documentId} in ${profile.guid}", e)
                            }
                        }
                    }
                    if (changed) {
                        myColl.save(profile)
                    }
                }
            } catch (e) {
                log.error("Some sort of error happened fixing the multimedia", e)
            }
        }
    }

    // TODO Remove this once all profiles have a status set
    def addStatusToProfiles() {
        log.info("Adding status to profiles")
        // Bypass GORM to set the last updated field directly
        MongoCollection<BasicDBObject> myColl = mongo.getDatabase(grailsApplication.config.grails.mongodb.databaseName).getCollection("profile", BasicDBObject.class)
        BasicDBList list = new BasicDBList()
        list.add(new BasicDBObject('profileStatus', new BasicDBObject('$exists', false)))
        list.add(new BasicDBObject('profileStatus', new BasicDBObject('$type', 10))) // $type: 10 is null
        BasicDBObject condition = new BasicDBObject('$or', list)

        //Find all docs missing a lastUpdated and set it to the dateCreated
        final cursor = myColl.find(condition)
        final count = cursor.size()
        cursor.each { DBObject profile ->
            profile.profileStatus = Profile.STATUS_PARTIAL
            myColl.save(profile)
        }
        log.info("Updated $count profiles")
    }

    def addScientificNameLowerToProfiles() {
        log.info("Adding scientificNameLower to profiles")

        MongoCollection<BasicDBObject> myColl = mongo.getDatabase(grailsApplication.config.grails.mongodb.databaseName).getCollection("profile", BasicDBObject.class)
        BasicDBList list = new BasicDBList()
        list.add(new BasicDBObject('scientificNameLower', new BasicDBObject('$exists', false)))
        list.add(new BasicDBObject('scientificNameLower', new BasicDBObject('$type', 10))) // $type: 10 is null
        BasicDBObject condition = new BasicDBObject('$or', list)

        // Find all docs missing a lastUpdated and set it to the dateCreated
        final cursor = myColl.find(condition)
        final count = cursor.size()
        cursor.each { DBObject profile ->
            profile.scientificNameLower = profile.scientificName?.toLowerCase()
            myColl.save(profile)
        }

        log.info("Updated $count profiles with scientificNameLower")
    }

    // TODO Remove this once all opera have dates set
    def addTimestampToOpera() {
        log.info("Adding dateCreated and lastUpdated to opera")
        // Bypass GORM to set the date created and last updated field directly
        MongoCollection<BasicDBObject> myColl = mongo.getDatabase(grailsApplication.config.grails.mongodb.databaseName).getCollection("opus", BasicDBObject.class)
        BasicDBList list = new BasicDBList()
        list.add(new BasicDBObject('lastUpdated', new BasicDBObject('$exists', false)))
        list.add(new BasicDBObject('lastUpdated', new BasicDBObject('$type', 10))) // $type: 10 is null
        BasicDBObject condition = new BasicDBObject('$or', list)

        // Find all docs missing a lastUpdated and set it to the dateCreated
        final cursor = myColl.find(condition)
        final count = cursor.size()
        cursor.each { DBObject opus ->
            opus.lastUpdated = new Date()
            myColl.save(opus)
        }

        log.info("Updated $count opera with last updated")

        list = new BasicDBList()
        list.add(new BasicDBObject('dateCreated', new BasicDBObject('$exists', false)))
        list.add(new BasicDBObject('dateCreated', new BasicDBObject('$type', 10))) // $type: 10 is null
        condition = new BasicDBObject('$or', list)

        // Find all docs missing a lastUpdated and set it to the dateCreated
        final cursor2 = myColl.find(condition)
        final count2 = cursor2.size()
        cursor2.each { DBObject opus ->
            opus.dateCreated = new Date()
            myColl.save(opus)
        }

        log.info("Updated $count2 opera with date created")
    }
}