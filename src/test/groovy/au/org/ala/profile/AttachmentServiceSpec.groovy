package au.org.ala.profile

import grails.core.DefaultGrailsApplication
import grails.testing.services.ServiceUnitTest
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Unroll

class AttachmentServiceSpec extends Specification implements ServiceUnitTest<AttachmentService> {

    def setup() {
        def application = new DefaultGrailsApplication()
        application.config.attachments.sound.maxFileSize = 5_000_000L
        service.grailsApplication = application
    }

    @Unroll
    def "validateSound accepts supported extension files"() {
        given:
        MultipartFile file = Stub(MultipartFile) {
            isEmpty() >> false
            getOriginalFilename() >> "call.${extension}"
            getContentType() >> contentType
            getSize() >> 1024L
        }

        expect:
        service.validateSound(file).valid

        where:
        extension | contentType
        'mp3'     | 'audio/mpeg'
        'wav'     | 'audio/wav'
        'wav'     | 'audio/x-wav'
    }

    def "validateSound rejects a file over the configured limit"() {
        given:
        service.grailsApplication.config.attachments.sound.maxFileSize = 100L
        MultipartFile file = Stub(MultipartFile) {
            isEmpty() >> false
            getOriginalFilename() >> 'call.mp3'
            getContentType() >> 'audio/mpeg'
            getSize() >> 101L
        }

        when:
        Map result = service.validateSound(file)

        then:
        !result.valid
        result.error.contains('100 bytes')

    }

    @Unroll
    def "validateSound rejects extension filename with content type contentType"() {
        given:
        MultipartFile file = Stub(MultipartFile) {
            isEmpty() >> false
            getOriginalFilename() >> filename
            getContentType() >> contentType
            getSize() >> 1024L
        }

        expect:
        !service.validateSound(file).valid

        where:
        filename   | contentType
        'call.pdf' | 'application/pdf'
        'call.mp3' | 'audio/wav'
        'call.wav' | 'audio/mpeg'
    }
}
