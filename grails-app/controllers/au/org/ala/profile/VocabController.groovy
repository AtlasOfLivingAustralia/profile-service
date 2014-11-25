package au.org.ala.profile

import grails.converters.JSON

class VocabController {

    def index() {
        def vocabs = Vocab.findAll()
        def vocabsToRender = []
        vocabs.each { vocab ->
            vocabsToRender << [
                "name":"${vocab.name}",
                "uuid":"${vocab.uuid}"

            ]
        }

        render vocabsToRender.sort { it.name.toLowerCase() } as JSON
    }

    def show(){
        def vocab = Vocab.findByUuid(params.uuid)
        if(vocab){
            def termsToRender = []
            vocab.terms.each { term ->
                termsToRender << [
                    "name":"${term.name}",
                    "uuid":"${term.uuid}"
                ]
            }

            def payload =  [name:vocab.name, terms: termsToRender.sort { it.name.toLowerCase() }]
            render payload as JSON
        } else {
            response.sendError(404)
        }
    }
}