package au.org.ala.profile

import com.codahale.metrics.MetricRegistry
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class ImportServiceSpec extends Specification implements ServiceUnitTest<ImportService> {
    void setup ( ) {
        defineBeans {
            metricRegistry(MetricRegistry)
        }
    }

    def 'test getRange'() {
        given:
        Attribute attribute = new Attribute()

        when:
        service.getRange("5 - 10", attribute)
        then:
        attribute.numberRange.from == 5.0
        attribute.numberRange.to == 10.0
        attribute.numberRange.fromInclusive == true
        attribute.numberRange.toInclusive == true

        when:
        attribute = new Attribute()
        service.getRange("(5 - 10]", attribute)
        then:
        attribute.numberRange.from == 5.0
        attribute.numberRange.to == 10.0
        attribute.numberRange.fromInclusive == false
        attribute.numberRange.toInclusive == true

        when:
        attribute = new Attribute()
        service.getRange("[5 - 10)", attribute)
        then:
        attribute.numberRange.from == 5.0
        attribute.numberRange.to == 10.0
        attribute.numberRange.fromInclusive == true
        attribute.numberRange.toInclusive == false

        when:
        attribute = new Attribute()
        service.getRange("not a range", attribute)
        then:
        attribute.numberRange == null
    }

    def 'test getNumbers'() {
        given:
        Attribute attribute = new Attribute()

        when:
        service.getNumbers("1, 2, 3, 4, 5", attribute)

        then:
        attribute.numbers == [1.0, 2.0, 3.0, 4.0, 5.0]

        when:
        attribute = new Attribute()
        service.getNumbers("", attribute)
        then:
        attribute.numbers == null

        when:
        attribute = new Attribute()
        service.getNumbers("1,2,3,4,5,", attribute)
        then:
        attribute.numbers == [1.0, 2.0, 3.0, 4.0, 5.0]

        when:
        attribute = new Attribute()
        service.getNumbers(null, attribute)
        then:
        attribute.numbers == null
    }

    def 'test getTerms'() {
        given:
        Attribute attribute = new Attribute()
        Map termConcurrentListVocabMapping = [:]
        Map vocabIdTermNameTermIdMapping = [:]
        Term title = new Term(name: "Test Title", uuid: "12345")
        MockImportService mockImportService = new MockImportService()
        mockImportService.vocabService = Mock(VocabService)
        mockImportService.vocabService.updateVocab(null, _) >> [vocab : [uuid: "vocab-uuid"]]

        when:
        mockImportService.getTerms("term1, term2, term3", attribute, title, termConcurrentListVocabMapping, vocabIdTermNameTermIdMapping)

        then:
        attribute.constraintList.size() == 3
        attribute.constraintList[0] == "term1-uuid"
        attribute.constraintList[1] == "term2-uuid"
        attribute.constraintList[2] == "term3-uuid"
        termConcurrentListVocabMapping[title.uuid] == "vocab-uuid"
        vocabIdTermNameTermIdMapping["vocab-uuidterm1"] == "term1-uuid"
        vocabIdTermNameTermIdMapping["vocab-uuidterm2"] == "term2-uuid"
        vocabIdTermNameTermIdMapping["vocab-uuidterm3"] == "term3-uuid"

        when:
        attribute = new Attribute()
        termConcurrentListVocabMapping = [:]
        vocabIdTermNameTermIdMapping = [:]
        mockImportService.getTerms("", attribute, title, termConcurrentListVocabMapping, vocabIdTermNameTermIdMapping)
        then:
        attribute.constraintList == null
        termConcurrentListVocabMapping[title.uuid] == null
        vocabIdTermNameTermIdMapping == [:]

        when:
        attribute = new Attribute()
        termConcurrentListVocabMapping = [:]
        vocabIdTermNameTermIdMapping = [:]
        mockImportService.getTerms(null, attribute, title, termConcurrentListVocabMapping, vocabIdTermNameTermIdMapping)
        then:
        attribute.constraintList == null
        termConcurrentListVocabMapping[title.uuid] == null
        vocabIdTermNameTermIdMapping == [:]
    }
}


class MockImportService extends ImportService {
    @Override
    Term getOrCreateTerm(String vocabId, String name) {
        new Term(uuid: "${name}-uuid")
    }
}