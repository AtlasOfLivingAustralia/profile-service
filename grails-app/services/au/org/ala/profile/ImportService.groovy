package au.org.ala.profile

import au.org.ala.profile.MasterListService.MasterListUnavailableException
import au.org.ala.profile.util.NSLNomenclatureMatchStrategy
import au.org.ala.profile.util.Utils
import com.google.common.collect.Sets
import com.mongodb.DBCollection
import com.mongodb.WriteConcern
import com.mongodb.WriteResult
import grails.converters.JSON
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.apache.http.HttpStatus
import org.grails.datastore.mapping.mongo.config.MongoCollection
import org.springframework.scheduling.annotation.Async

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger

import static groovyx.gpars.GParsPool.*

class ImportService extends BaseDataAccessService {

    static final int IMPORT_THREAD_POOL_SIZE = 10

    ProfileService profileService
    NameService nameService
    def grailsApplication
    def masterListService

    Term getOrCreateTerm(String vocabId, String name) {
        Vocab vocab = Vocab.findByUuid(vocabId)
        Term term = Term.findByNameAndVocab(name, vocab)
        if (!term) {
            term = new Term(name: name, vocab: vocab)
            term.save(flush: true)
        }
        term
    }

    /**
     * Profile import is a two-pass operation:
     * 1.) Use multiple threads to loop through the provided data set to find all values that should be unique (e.g. attribute terms, contributors, etc)
     * 1.1) Using a single thread, store them in the database and return a map of references.
     * 2.) Use multiple threads to loop through the provided data set again to process each individual record, retrieving references to unique values from the maps from pass 1.
     *
     * Querying data from the database, and writing records if they don't exist, is the slowest part of the import process.
     * Executing these 'get or create' actions concurrently results in race conditions and duplicated data.
     * Synchronising the methods resolves this issue, but dramatically increases the execution time of the import.
     *
     * The two-pass approach ensures no duplicate records are created, and does not significantly impact performance.
     *
     * @param importId a unique identifier assigned to this import process, to be returned the service consumer so they can request the import report when the import completes
     * @param opusId
     * @param profilesJson
     */
    @Async
    void importProfiles(String importId, String opusId, profilesJson) {
        File importReportFile = new File("${grailsApplication.config.temp.file.directory}/${importId}.json.inprogress")
        importReportFile.createNewFile()

        Opus opus = Opus.findByUuid(opusId);

        Map<String, Map> profileResults = [:] as ConcurrentHashMap
        Date startTime = new Date()

        AtomicInteger success = new AtomicInteger(0)
        AtomicInteger index = new AtomicInteger(0)
        int reportInterval = Math.max(0.05 * profilesJson.size(), 5.0) // log at 5% intervals

        Map uniqueValues = findAndStoreUniqueValues(opus, profilesJson)
        Map<String, Term> vocab = uniqueValues.vocab
        Map<String, Contributor> contributors = uniqueValues.contributors

        boolean enableNSLMatching = true

        Map nslNamesCached = enableNSLMatching ? nameService.loadNSLSimpleNameDump() : [:]

        if (!nslNamesCached) {
            log.warn("NSL Simple Name cache failed - using live service lookup instead")
        }

        log.info "Importing profiles ..."
        withPool(IMPORT_THREAD_POOL_SIZE) {
            profilesJson.eachParallel {
                Map results = [errors: [], warnings: []]
                try {
                    def currentIndex = index.incrementAndGet()

                    if (!it.scientificName) {
                        results.errors << "Failed to import row ${currentIndex}, does not have a scientific name"
                    } else {
                        Map matchedName = nameService.matchName(it.scientificName?.trim(), it.classification ?: [:])

                        String scientificName = matchedName?.scientificName?.trim() ?: it.scientificName.trim()
                        String fullName = matchedName?.fullName?.trim() ?: scientificName.trim()
                        String nameAuthor = matchedName?.nameAuthor?.trim() ?: null
                        String guid = matchedName?.guid ?: null

                        if (!matchedName) {
                            results.warnings << "ALA - No matching name for ${it.scientificName} in the ALA"
                        } else if (!it.scientificName.equalsIgnoreCase(matchedName.scientificName) && !it.scientificName.equalsIgnoreCase(fullName)) {
                            results.warnings << "ALA - Provided with name ${it.scientificName}, but was matched with name ${fullName} in the ALA. Using provided name."
                            scientificName = it.scientificName
                            fullName = it.fullName
                            nameAuthor = it.nameAuthor
                        }

                        Profile profile = Profile.findByScientificNameAndOpus(scientificName, opus)
                        if (profile && profile.profileStatus != Profile.STATUS_EMPTY) {
                            log.info("Profile already exists in this opus for scientific name ${scientificName}")
                            results.errors << "'${it.scientificName}' already exists (provided as ${it.scientificName}, matched as ${fullName})"
                        } else {
                            if (!profile) {
                                profile = new Profile(scientificName: scientificName, nameAuthor: nameAuthor, opus: opus, guid: guid, attributes: [], links: [], bhlLinks: [], bibliography: [], profileStatus: Profile.STATUS_LEGACY);
                            } else {
                                profile.scientificName = scientificName
                                profile.nameAuthor = nameAuthor
                                profile.opus = opus
                                profile.guid = guid
                                profile.profileStatus = Profile.STATUS_LEGACY
                            }
                            profile.fullName = fullName

                            if (matchedName) {
                                profile.matchedName = new Name(matchedName)
                            }

                            if (it.nslNameIdentifier) {
                                profile.nslNameIdentifier = it.nslNameIdentifier
                            } else if (enableNSLMatching) {
                                Map nslMatch
                                boolean matchedByName
                                if (it.nslNomenclatureIdentifier) {
                                    nslMatch = nameService.findNslNameFromNomenclature(it.nslNomenclatureIdentifier)
                                    matchedByName = false
                                } else if (nslNamesCached) {
                                    nslMatch = nameService.matchCachedNSLName(nslNamesCached, it.scientificName, it.nameAuthor, it.fullName)
                                    matchedByName = true
                                } else {
                                    nslMatch = nameService.matchNSLName(it.scientificName)
                                    matchedByName = true
                                }

                                if (nslMatch) {
                                    profile.nslNameIdentifier = nslMatch.nslIdentifier
                                    profile.nslProtologue = nslMatch.nslProtologue
                                    if (!profile.nameAuthor) {
                                        profile.nameAuthor = nslMatch.nameAuthor
                                    }

                                    if (matchedByName && !it.scientificName.equalsIgnoreCase(nslMatch.scientificName) && !it.scientificName.equalsIgnoreCase(nslMatch.fullName)) {
                                        results.warnings << "NSL - Provided with name ${it.scientificName}, but was matched with name ${nslMatch.fullName} in the NSL. Using provided name."
                                    }
                                } else {
                                    results.warnings << "NSL - No matching name for ${it.scientificName} in the NSL."
                                }
                            }

                            if (profile.guid) {
                                profileService.populateTaxonHierarchy(profile)
                            }

                            if (it.nslNomenclatureIdentifier) {
                                profile.nslNomenclatureIdentifier = it.nslNomenclatureIdentifier
                            } else if (profile.nslNameIdentifier && enableNSLMatching && it.nslNomenclatureMatchStrategy) {
                                NSLNomenclatureMatchStrategy matchStrategy = NSLNomenclatureMatchStrategy.valueOf(it.nslNomenclatureMatchStrategy) ?: NSLNomenclatureMatchStrategy.DEFAULT
                                if (matchStrategy != NSLNomenclatureMatchStrategy.NONE) {
                                    Map nomenclature = nameService.findNomenclature(profile.nslNameIdentifier, matchStrategy, it.nslNomenclatureMatchData)

                                    if (!nomenclature) {
                                        results.warnings << "No matching nomenclature was found for '${it.nslNomenclatureMatchData}'"
                                    }

                                    profile.nslNomenclatureIdentifier = nomenclature?.id
                                }
                            }

                            it.links.each {
                                if (it) {
                                    profile.links << createLink(it, contributors)
                                }
                            }

                            it.bhl.each {
                                if (it) {
                                    profile.bhlLinks << createLink(it, contributors)
                                }
                            }

                            it.bibliography.each {
                                if (it) {
                                    profile.bibliography << new Bibliography(uuid: UUID.randomUUID().toString(), text: it, order: profile.bibliography.size())
                                }
                            }

                            Set<String> contributorNames = []
                            it.attributes.each {
                                if (it.title && it.text) {
                                    Term term = vocab.get(it.title.trim())

                                    String text = it.stripHtml?.booleanValue() ? Utils.cleanupText(it.text) : it.text
                                    if (text?.trim()) {
                                        Attribute attribute = new Attribute(title: term, text: text)
                                        attribute.uuid = UUID.randomUUID().toString()

                                        if (it.creators) {
                                            attribute.creators = []
                                            it.creators.each {
                                                String name = cleanName(it)
                                                if (name) {
                                                    Contributor contrib = contributors[name]
                                                    if (contrib) {
                                                        attribute.creators << contrib
                                                        contributorNames << contrib.name
                                                    } else {
                                                        log.warn("Missing contributor for name '${name}'")
                                                    }
                                                }
                                            }
                                        }

                                        if (it.editors) {
                                            attribute.editors = []
                                            it.editors.each {
                                                String name = cleanName(it)
                                                if (name) {
                                                    attribute.editors << contributors[name]
                                                }
                                            }
                                        }

                                        attribute.profile = profile
                                        profile.attributes << attribute
                                    }
                                }
                            }

                            if (it.images) {
                                it.images.each {
                                    uploadImage(scientificName, opus.dataResourceUid, it)
                                }
                            }

                            if (it.authorship) {
                                profile.authorship = it.authorship.collect {
                                    Term term = getOrCreateTerm(opus.authorshipVocabUuid, it.category)
                                    new Authorship(category: term, text: it.text)
                                }
                            } else {
                                Term term = getOrCreateTerm(opus.authorshipVocabUuid, "Author")
                                profile.authorship = [new Authorship(category: term, text: contributorNames.join(", "))]
                            }

                            profile.save(flush: true)

                            if (profile.errors.allErrors.size() > 0) {
                                log.error("Failed to save ${profile}")
                                profile.errors.each { log.error(it) }
                                results.errors << "Failed to save profile ${profile.errors.allErrors.get(0)}"
                            } else {
                                def currentSuccess = success.incrementAndGet()
                                if (currentIndex % reportInterval == 0) {
                                    log.debug("Saved ${currentSuccess} of ${profilesJson.size()}")
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error "An exception occurred while importing the record ${it}", e
                    results.errors << "Failed to create profile ${it.scientificName}: ${e.getMessage()}"
                }

                results.status = results.errors ? "error" : results.warnings ? "warning" : "success"
                profileResults << [(it.scientificName): results]
            }
        }
        log.debug "${success} of ${profilesJson.size()} records imported"

        Date finishTime = new Date()

        importReportFile << ([started: startTime.format("dd/MM/yyyy HH:mm:ss"),
                              finished: finishTime.format("dd/MM/yyyy HH:mm:ss"),
                              profiles: profileResults] as JSON)

        importReportFile.renameTo("${grailsApplication.config.temp.file.directory}/${importId}.json")
    }

    def uploadImage(String scientificName, String dataResourceId, Map metadata) {
        Map payload = [scientificName: scientificName, multimedia: [metadata]]

        try {
            RESTClient client = new RESTClient("${grailsApplication.config.image.upload.url}dr3")
            def resp = client.post(headers: ["User-Agent": "groovy"],
                    query: [apiKey: "${grailsApplication.config.image.upload.apiKey}"],
                    requestContentType: ContentType.JSON,
                    body: payload)

            if (resp.status != HttpStatus.SC_OK && resp.status != HttpStatus.SC_CREATED) {
                log.warn("Failed to upload image: ${resp.data}")
            }
        }
        catch (Exception e) {
            log.warn("Failed to upload image: ", e)
        }
    }

    Map findAndStoreUniqueValues(Opus opus, profilesJson) {
        Set<String> uniqueTerms = [] as ConcurrentSkipListSet
        Set<String> uniqueContributors = [] as ConcurrentSkipListSet

        log.info "Retrieving unique data..."
        withPool(IMPORT_THREAD_POOL_SIZE) {
            profilesJson.eachParallel {
                it.attributes.each { attr ->
                    if (attr.title) {
                        uniqueTerms << attr.title.trim()
                    }

                    attr.editors?.each { name ->
                        uniqueContributors << cleanName(name)
                    }
                    attr.creators?.each { name ->
                        uniqueContributors << cleanName(name)
                    }
                }
            }
        }

        log.info "Storing unique vocabulary (${uniqueTerms.size()} terms) ..."
        Map<String, Term> vocab = [:]
        uniqueTerms.each {
            vocab[it] = getOrCreateTerm(opus.attributeVocabUuid, it)
        }

        log.info "Storing unique contributors (${uniqueContributors.size()} names) ..."
        Map<String, Contributor> contributors = [:]
        uniqueContributors.each {
            contributors[it] = getOrCreateContributor(it, opus.dataResourceUid)
        }

        [vocab: vocab, contributors: contributors]
    }

    private static cleanName(String name) {
        name.replaceAll("\\(.*\\)", "").replaceAll(" +", " ").trim()
    }

    Contributor getOrCreateContributor(String name, String opusDataResourceId) {
        Contributor contributor = Contributor.findByName(name)
        if (!contributor) {
            contributor = new Contributor(name: name, uuid: UUID.randomUUID().toString(), dataResourceUid: opusDataResourceId)
        }
        contributor
    }

    Link createLink(data, contributors) {
        Link link = new Link(data)
        link.uuid = UUID.randomUUID().toString()

        if (data.creators) {
            link.creators = []
            data.creators.each {
                String name = cleanName(it)
                if (name) {
                    link.creators << contributors[name]
                }
            }
        }

        link
    }

    def syncMasterList(Opus collection) {

        def masterList
        try {
            masterList = masterListService.getMasterList(collection)
        } catch (MasterListUnavailableException e) {
            log.error("Master list for ${collection.shortName} unavailable, skipping import")
        }

        log.info("Syncing Master List for ${collection.shortName} with ${masterList.size()} entries")
        boolean enableNSLMatching = true

        Map nslNamesCached = enableNSLMatching ? nameService.loadNSLSimpleNameDump() : [:]

        if (!nslNamesCached) {
            log.warn("NSL Simple Name cache failed - using live service lookup instead")
        }

        log.info "Importing master list ..."
        withPool(IMPORT_THREAD_POOL_SIZE, logException("syncMasterList(${collection.shortName})")) {
//            Map results = [errors: [], warnings: []]
            def matches = masterList.collectParallel {
                [match: nameService.matchName(it.name, [:]), listItem: it]
            }
            log.info("Sync Master List for ${collection.shortName} matched ${matches.size()} records")

            def matchesMap = matches.collectEntries { [(it.listItem.name.trim()): it] }
//            def namesMap = masterList.collectEntries { [(it.name): it]}

            def names = masterList.collect { it.name?.trim() }

            DBCollection pc = (DBCollection) Profile.collection
            WriteResult deletes = pc.remove(['scientificName': ['$nin': names ], 'opus': collection.id, 'profileStatus': Profile.STATUS_EMPTY ])

            log.info("Sync Master List for ${collection.shortName} deleted ${deletes.n} existing empty records which do not exist on master list")

            def existingProfiles = Profile.findAllByOpusAndScientificNameInList(collection, names)
            def groups = existingProfiles.groupBy {
                it.profileStatus == Profile.STATUS_EMPTY
            }

            def existingEmpty = groups[true]
            def updated = groups[false]

            log.info("Sync Master List for ${collection.shortName} found ${existingEmpty?.size() ?: 0} existing empty records")
            log.info("Sync Master List for ${collection.shortName} found ${updated?.size() ?: 0} existing non-empty records")

            def newNames = Sets.difference(names.toSet(), existingProfiles*.scientificName.toSet())
//            def unmatchedNames = matches.findAll { !it.match }*.listItem*.name

            def inserts = newNames.collectParallel {
                def match = matchesMap[it]
                Map results = [errors: [], warnings: []]
                def emptyProfile = generateEmptyProfile(collection, match.listItem, match.match, nslNamesCached, results)
                log.info("Sync Master List for ${collection.shortName} results $results")
                emptyProfile
            }

//            def existingProfiles2 = Profile.findAllByOpusAndScientificNameInListAndGuidIsEmpty(collection, unmatchedNames)
//
//            def newNames = Sets.difference(unmatchedNames.toSet(), existingProfiles2*.scientificName.toSet())
//
//            def inserts2 = newNames.collectParallel {
//                def listItem = namesMap[it]
//                generateEmptyProfile(collection, listItem, null, nslNamesCached, results)
//            }

            def ids = Profile.saveAll(inserts)
            log.info("Sync Master List for ${collection.shortName} inserted ${ids.size()} empty records")
        }
    }

    def generateEmptyProfile(opus, listItem, matchedName, nslNamesCached, results) {
        // Always assign the name as the list name, because the list is the source of truth
        String scientificName = listItem.name.trim()
//        String scientificName = matchedName?.scientificName?.trim() ?: listItem.name.trim()
        String fullName = matchedName?.fullName?.trim() ?: matchedName?.scientificName?.trim() ?: listItem.name.trim()
        String nameAuthor = matchedName?.nameAuthor?.trim() ?: null
        String guid = matchedName?.guid ?: null

        if (!matchedName) {
            results.warnings << "ALA - No matching name for ${listItem.name} in the ALA"
        } else if (!listItem.name.equalsIgnoreCase(matchedName.scientificName) && !listItem.name.equalsIgnoreCase(fullName)) {
            results.warnings << "ALA - Provided with name ${listItem.name}, but was matched with name ${fullName} in the ALA. Using provided name."
//            scientificName = listItem.name
            fullName = listItem.fullName
            nameAuthor = listItem.nameAuthor
        }


        Profile profile = new Profile(scientificName: scientificName, nameAuthor: nameAuthor, opus: opus, guid: guid, attributes: [], links: [], bhlLinks: [], bibliography: [], profileStatus: Profile.STATUS_EMPTY)
        profile.fullName = fullName

        if (matchedName) {
            profile.matchedName = new Name(matchedName)
        }

        if (listItem.nslNameIdentifier) {
            profile.nslNameIdentifier = listItem.nslNameIdentifier
        } else {
            Map nslMatch
            boolean matchedByName
            if (listItem.nslNomenclatureIdentifier) {
                nslMatch = nameService.findNslNameFromNomenclature(listItem.nslNomenclatureIdentifier)
                matchedByName = false
            } else if (nslNamesCached) {
                nslMatch = nameService.matchCachedNSLName(nslNamesCached, listItem.name, listItem.nameAuthor, listItem.fullName ?: listItem.name)
                matchedByName = true
            } else {
                nslMatch = nameService.matchNSLName(listItem.name)
                matchedByName = true
            }

            if (nslMatch) {
                profile.nslNameIdentifier = nslMatch.nslIdentifier
                profile.nslProtologue = nslMatch.nslProtologue
                if (!profile.nameAuthor) {
                    profile.nameAuthor = nslMatch.nameAuthor
                }

                if (matchedByName && !listItem.name.equalsIgnoreCase(nslMatch.scientificName) && !listItem.name.equalsIgnoreCase(nslMatch.fullName)) {
                    results.warnings << "NSL - Provided with name ${listItem.name}, but was matched with name ${nslMatch.fullName} in the NSL. Using provided name."
                }
            } else {
                results.warnings << "NSL - No matching name for ${listItem.name} in the NSL."
            }
        }

        if (profile.guid) {
            profileService.populateTaxonHierarchy(profile)
        }

        return profile
    }

    private Thread.UncaughtExceptionHandler logException(String prefix) {
//        return UncaughtEx
        new Thread.UncaughtExceptionHandler() {
            @Override
            void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception for $prefix", e)
            }
        }
    }
}
