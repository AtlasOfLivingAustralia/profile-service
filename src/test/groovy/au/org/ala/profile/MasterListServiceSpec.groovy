package au.org.ala.profile

import au.org.ala.ws.service.WebService
import com.codahale.metrics.MetricRegistry
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification


class MasterListServiceSpec extends Specification implements ServiceUnitTest<MasterListService>{

    Closure doWithSpring() {{->
        metricRegistry(MetricRegistry)
    }}

    def setup() {
        grailsApplication.config.lists.items.cacheSpec = 'maximumSize=0' // disable cache for tests
        service.init()
    }

    def 'getMasterList always trims names'() {
        given:
        service.webService = Stub(WebService)
        service.webService.get(_) >> [
                status: 200,
                resp: [
                        [name: ' a '],
                        [name: null],
                        [name: 'b'],
                        [name: ' c'],
                        [name: 'd ']
                ]
        ]
        def opus = new Opus(masterListUid: 'test')

        when:
        def results = service.getMasterList(opus)

        then:

        results.each {
            it?.name?.trim() == it?.name
        }

    }

    def 'an unavailable florula list is not applied'() {
        given:
        service.webService = Stub(WebService)
        service.webService.get(_) >> { String url ->
            if (url.contains('missing')) {
                [statusCode: 404, resp: null]
            } else {
                [statusCode: 200, resp: [[name: 'Acacia']]]
            }
        }
        def opus = new Opus(masterListUid: 'master')

        when:
        def withMasterList = service.getCombinedListForUser(opus, 'missing')
        def withoutMasterList = service.getCombinedListForUser(new Opus(), 'missing')

        then:
        withMasterList*.name == ['Acacia']
        withoutMasterList == null
    }

    def 'checkFlorulaList distinguishes a missing list from an outage'() {
        given:
        service.webService = Stub(WebService)
        service.webService.get(_) >> { String url ->
            if (url.contains('/missing')) {
                [statusCode: 404]
            } else if (url.contains('/down')) {
                [statusCode: 503]
            } else {
                [statusCode: 200, resp: [dataResourceUid: 'dr1']]
            }
        }

        expect:
        service.checkFlorulaList('dr1') == 204
        service.checkFlorulaList('missing') == 404
        service.checkFlorulaList('down') == 502
        service.checkFlorulaList('') == 400
    }
}
