package au.org.ala.profile

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import static groovyx.gpars.GParsPool.*

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4
import org.xml.sax.SAXException


class ImportService extends BaseDataAccessService {

    static final int IMPORT_THREAD_POOL_SIZE = 10

    def nameService

    def foaOpusId = "2f75e6c9-7034-409b-b27c-3864326bee41"
    def spongesOpusId = "e3e35631-d864-44ed-a0b1-2c707bbc6d61"

    def importSponges() {
        def spongeOpus = Opus.findByDataResourceUid("dr824")
        if (!spongeOpus) {
            spongeOpus = new Opus(
                    opusId: spongesOpusId,
                    dataResourceUid: "dr824",
                    title: "Spongemaps",
                    imageSources: ["dr344"],
                    recordSources: ["dr344"],
                    logoUrl: "http://collections.ala.org.au/data/institution/QMN_logo.jpg",
                    bannerUrl: "http://images.ala.org.au/store/a/0/5/0/12c3a0cc-8a7a-4731-946a-6d481a60050a/thumbnail_large",
                    enablePhyloUpload: true,
                    enableOccurrenceUpload: true,
                    enableTaxaUpload: true,
                    enableKeyUpload: true
            )
            spongeOpus.save(flush: true)

            spongeOpus.getErrors().getAllErrors().each { println it }
        }
    }

    def cleanupText(str) {
        if (str) {
            str = unescapeHtml4(str)
            // preserve line breaks as new lines, remove all other html
            str = str.replaceAll(/<p\/?>|<br\/?>/, "\n").replaceAll(/<.+?>/, "").trim()
        }
        return str
    }

    Term getOrCreateTerm(String vocabId, String name) {
        Vocab vocab = Vocab.findByUuid(vocabId)
        Term term = Term.findByNameAndVocab(name, vocab)
        if (!term) {
            term = new Term(name: name, vocab: vocab)
            term.save(flush: true)
        }
        term
    }

    def importFOA() {
        String FLORA_AUSTRALIA_VOCAB = "7dba0bab-65d2-4a22-a682-c13b4e301f70"

        def opusModel = [
                opusId                : foaOpusId,
                dataResourceUid       : "dr382",
                title                 : "Flora of Australia",
                imageSources          : ["dr382", "dr413", "dr689"],
                recordSources         : ["dr376"],
                logoUrl               : "https://fieldcapture.ala.org.au/static/RrjzrZ0Ci0GPLETIr8x8KUMjfJtZKvifrUtMCedwKRB.png",
                bannerUrl             : "http://www.anbg.gov.au/images/photo_cd/FLIND_RANGES/fr-3_3.jpg",
                attributeVocabUuid    : FLORA_AUSTRALIA_VOCAB,
                enablePhyloUpload     : false,
                enableOccurrenceUpload: false,
                enableTaxaUpload      : false,
                enableKeyUpload       : false,
                mapAttribution        : 'Australian Virtual Herbarium (CHAH)',
                biocacheUrl           : 'http://avh.ala.org.au',
                biocacheName          : 'Australian Virtual Herbarium'
        ]

        def foaOpus = Opus.findByDataResourceUid("dr382")
        if (!foaOpus) {
            foaOpus = new Opus(opusModel)
            foaOpus.save(flush: true)
        }

        Vocab vocab = Vocab.findByUuid(FLORA_AUSTRALIA_VOCAB)
        if (!vocab) {
            vocab = new Vocab(uuid: FLORA_AUSTRALIA_VOCAB, name: "Flora of Australia Vocabulary")
            save vocab
        }

        Term habitatTerm = getOrCreateTerm(FLORA_AUSTRALIA_VOCAB, "Habitat")
        Term descriptionTerm = getOrCreateTerm(FLORA_AUSTRALIA_VOCAB, "Description")
        Term distributionTerm = getOrCreateTerm(FLORA_AUSTRALIA_VOCAB, "Distribution")

        new File("/data/foa").listFiles().each {
            println "Processing ${it.name}..."
            try {
                def foaProfile = new XmlParser().parseText(it.text)

                def contributors = []

                foaProfile.ROWSET.ROW.CONTRIBUTORS?.CONTRIBUTORS_ITEM?.each {
                    contributors << cleanupText(it.CONTRIBUTOR.text())
                }

                def distributions = []
                foaProfile.ROWSET.ROW.DISTRIBUTIONS?.DISTRIBUTIONS_ITEM?.each {
                    distributions << cleanupText(it.DIST_TEXT.text())
                }

                def parsed = [
                        scientificName: foaProfile.ROWSET.ROW.TAXON_NAME?.text(),
                        habitat       : cleanupText(foaProfile.ROWSET.ROW?.HABITAT?.text()),
                        source        : cleanupText(foaProfile.ROWSET.ROW.SOURCE.text()),
                        description   : cleanupText(foaProfile.ROWSET.ROW.DESCRIPTION?.text()),
                        distributions : distributions,
                        contributor   : contributors
                ]

                if (parsed.scientificName) {

                    //lookup GUID
                    def guid = nameService.getGuidForName(parsed.scientificName)

                    //add a match to APC / APNI
                    def profile = new Profile([
                            profileId     : UUID.randomUUID().toString(),
                            guid          : guid,
                            scientificName: parsed.scientificName,
                            opus          : foaOpus
                    ])

                    profile.attributes = []

                    if (parsed.habitat) {
                        profile.attributes << new Attribute(uuid: UUID.randomUUID().toString(), title: habitatTerm, text: parsed.habitat, profile: profile)
                    }
                    if (parsed.description) {
                        profile.attributes << new Attribute(uuid: UUID.randomUUID().toString(), title: descriptionTerm, text: parsed.description, profile: profile)
                    }

                    parsed.distributions.each {
                        if (it) {
                            profile.attributes << new Attribute(uuid: UUID.randomUUID().toString(), title: distributionTerm, text: it, profile: profile)
                        }
                    }

                    //associate the contributors with all attributes
                    def contribs = []
                    contributors.each {
                        def retrieved = Contributor.findByName(it)
                        if (retrieved) {
                            contribs << retrieved
                        } else {
                            contribs << new Contributor(uuid: UUID.randomUUID().toString(), name: it, dataResourceUid: foaOpus.dataResourceUid)
                        }
                    }

                    def oldFoaLink = new Link(
                            uuid: UUID.randomUUID().toString(),
                            title: parsed.scientificName,
                            description: "Old Flora of Australia site page for ${parsed.scientificName}",
                            url: "http://www.anbg.gov.au/abrs/online-resources/flora/stddisplay.xsql?pnid=" + it.getName().replace(".xml", "")
                    )

                    profile.links = [oldFoaLink]

                    profile.attributes.each {
                        it.creators = contribs
                    }

                    profile.save(flush: true)

                    profile.errors.allErrors.each {
                        println(it)
                    }
                }
            } catch (SAXException se) {
                //se.printStackTrace()
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }

    Map<String, String> importProfiles(String opusId, profilesJson) {
        Opus opus = Opus.findByUuid(opusId);

        Map<String, String> results = [:] as ConcurrentHashMap

        AtomicInteger success = new AtomicInteger(0)
        AtomicInteger index = new AtomicInteger(0)
        int reportInterval = 0.05 * profilesJson.size() // log at 5% intervals

        withPool(IMPORT_THREAD_POOL_SIZE) {
            profilesJson.eachParallel {
                index.incrementAndGet()
                Profile profile = Profile.findByScientificNameAndOpus(it.scientificName, opus);
                if (profile) {
                    log.info("Profile already exists in this opus for scientific name ${it.scientificName}")
                    results << [(it.scientificName): "Already exists"]
                } else {
                    if (!it.scientificName) {
                        results << [("Row${index}"): "Failed to import row ${index}, does not have a scientific name"]
                    } else {
                        List<String> guidList = nameService.getGuidForName(it.scientificName)
                        String guid = null
                        if (guidList && guidList.size() > 0) {
                            guid = guidList[0]
                        }

                        profile = new Profile(scientificName: it.scientificName, opus: opus, guid: guid, attributes: [], links: [], bhlLinks: []);

                        it.links.each {
                            if (it) {
                                profile.links << createLink(it)
                            }
                        }

                        it.bhl.each {
                            if (it) {
                                profile.bhlLinks << createLink(it)
                            }
                        }

                        Set<String> contributorNames = []
                        it.attributes.each {
                            if (it.title && it.text) {
                                Term term = getOrCreateTerm(opus.attributeVocabUuid, it.title)

                                String text = cleanupText(it.text)
                                if (text) {
                                    Attribute attribute = new Attribute(title: term, text: text)
                                    attribute.uuid = UUID.randomUUID().toString()

                                    if (it.creators) {
                                        attribute.creators = []
                                        it.creators.each {
                                            Contributor contrib = getOrCreateContributor(it, opus.dataResourceUid)
                                            attribute.creators << contrib
                                            contributorNames << contrib.name
                                        }
                                    }

                                    if (it.editors) {
                                        attribute.editors = []
                                        it.editors.each {
                                            attribute.editors << getOrCreateContributor(it, opus.dataResourceUid)
                                        }
                                    }

                                    attribute.profile = profile
                                    profile.attributes << attribute
                                }
                            }
                        }

                        profile.authorship = [new Authorship(category: "Author", text: contributorNames.join(", "))]

                        profile.save(flush: true)

                        if (profile.errors.allErrors.size() > 0) {
                            log.error("Failed to save ${profile}")
                            profile.errors.each { log.error(it) }
                            results << [(it.scientificName): "Failed: ${profile.errors.allErrors.get(0)}"]
                        } else {
                            results << [(it.scientificName): "Success"]
                            success.incrementAndGet()
                            if (index % reportInterval == 0) {
                                log.debug("Saved ${success} of ${profilesJson.size()}")
                            }
                        }
                    }
                }
            }
        }
        log.debug "${success} of ${profilesJson.size()} records imported"

        results
    }

    Link createLink(data) {
        Link link = new Link(data)
        link.uuid = UUID.randomUUID().toString()

        if (data.creators) {
            link.creators = []
            data.creators.each {
                link.creators << new Contributor(name: it)
            }
        }

        link
    }

    Contributor getOrCreateContributor(String name, String opusDataResourceId) {
        Contributor contributor = Contributor.findByName(name)
        if (!contributor) {
            contributor = new Contributor(name: name, uuid: UUID.randomUUID().toString(), dataResourceUid: opusDataResourceId)
        }
        contributor
    }

}
