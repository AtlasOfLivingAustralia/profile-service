package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON
import groovy.json.JsonSlurper

@RequireApiKey
class ImportController extends BaseController {

    static allowedMethods = [vocab: "POST", profile: "POST", glossary: "POST"]

    VocabService vocabService
    ImportService importService
    OpusService opusService

    def vocab() {
        def json = request.getJSON()
        if (!json || !json.opusId || !json.terms) {
            badRequest(samples.vocab)
        } else {
            Opus opus = Opus.findByUuid(json.opusId)

            vocabService.updateVocab(opus.attributeVocabUuid, json)
        }
    }

    def glossary() {
        def json = request.getJSON()
        if (!json || (!json.glossaryId && !json.opusId)) {
            badRequest(samples.glossary)
        } else {
            opusService.saveGlossaryItems(json.opusId, json)
        }
    }

    def profile() {
        def json = request.getJSON()
        if (!json || !json.opusId || !json.profiles) {
            badRequest(samples.profile)
        } else {
            Opus opus = Opus.findByUuid(json.opusId)
            if (!opus) {
                notFound "Opus ${json.opusId} does not exist."
            } else {
                String importId = UUID.randomUUID().toString()

                importService.importProfiles(importId, json.opusId, json.profiles)

                render ([status: "IN_PROGRESS", id: importId, report: ""] as JSON)
            }
        }
    }

    def report() {
        if (!params.importId) {
            badRequest "importId is a required parameter"
        } else {
            File reportFile = new File("${grailsApplication.config.getProperty('temp.file.directory')}/${params.importId}.json")

            if (!reportFile.exists()) {
                reportFile = new File("${grailsApplication.config.getProperty('temp.file.directory')}/${params.importId}.json.inprogress")

                if (!reportFile.exists()) {
                    notFound "No matching import report was found for id ${params.id}"
                } else {
                    render ([status: "IN_PROGRESS", id: params.importId, report: ""] as JSON)
                }
            } else {
                Map report = new JsonSlurper().parseText(reportFile.text)
                render ([status: "COMPLETE", id: params.importId, report: report] as JSON)
            }
        }
    }

    Map samples = [
            vocab: """{"opusId": "", "strict": "true|false", "deleteExisting": "true|false", "terms": ["term1", "term2", "..."]}""",
            glossary: """{"opusId|glossaryId": "", "items": [{"itemId": "", "term": "", "definition": "", "cf": ["term1","term2", "..."]}]}""",
            profile: """{
                    "opusId": "",
                    "profiles":[{
                            "scientificName": "",
                            "nameAuthor": "",
                            "links":[{
                                    "creators": [""],
                                    "edition": "",
                                    "title": "",
                                    "publisherName": "",
                                    "fullTitle":"",
                                    "description": "",
                                    "url":"",
                                    "doi":""
                                    }],
                            "bhl":[{
                                    "creators": [""],
                                    "edition": "",
                                    "title": "",
                                    "publisherName": "",
                                    "fullTitle":"",
                                    "description": "",
                                    "url":"",
                                    "doi":""
                                    }],
                            "attributes": [{
                                    "creators": [""],
                                    "editors": [""],
                                    "title": "",
                                    "text": ""
                                    }],
                            "authorship": [{
                                    "category": "",
                                    "text": ""
                                    }],
                            "images": [{
                                    "identifier": "url to the image - required",
                                    "title": "required",
                                    "creator": "",
                                    "created": ""
                                    }]
                        }]
                     }"""
    ]
}
