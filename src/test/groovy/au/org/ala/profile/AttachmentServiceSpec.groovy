package au.org.ala.profile

import grails.core.DefaultGrailsApplication
import grails.testing.services.ServiceUnitTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Unroll
import java.nio.file.Files

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

    def "saveDraftSoundReplacement stages a replacement without modifying the published sound"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "sound-1"

        File published = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        published.parentFile.mkdirs()
        published.bytes = "original recording".bytes

        MockMultipartFile replacement = new MockMultipartFile(
            "file",
            "replacement.mp3",
            "audio/mpeg",
            "replacement recording".bytes
        )

        when:
        service.saveDraftSoundReplacement(opusId, profileId, attachmentId, replacement, "mp3")

        then:
        published.text == "original recording"

        and:
        File staged = service.getDraftSoundReplacement(
            opusId, profileId, attachmentId, "mp3"
        )
        staged != null
        staged.text == "replacement recording"

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

    def "replaceDraftOnlySound replaces MP3 with WAV and removes the old file"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "draft-sound-1"

        File original = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        original.parentFile.mkdirs()
        original.text = "original MP3 recording"

        MockMultipartFile replacement = new MockMultipartFile(
            "file",
            "replacement.wav",
            "audio/wav",
            "replacement WAV recording".bytes
        )

        when:
        service.replaceDraftOnlySound(
            opusId,
            profileId,
            attachmentId,
            replacement,
            "mp3",
            "wav"
        )

        then:
        File updated = service.getAttachment(
            opusId, profileId, attachmentId, "wav"
        )

        updated != null
        updated.text == "replacement WAV recording"

        and:
        !original.exists()

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

    def "replaceDraftOnlySound replaces MP3 with MP3 using the same attachment ID"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "draft-sound-1"

        File original = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        original.parentFile.mkdirs()
        original.text = "original MP3 recording"

        MockMultipartFile replacement = new MockMultipartFile(
            "file",
            "replacement.mp3",
            "audio/mpeg",
            "updated MP3 recording".bytes
        )

        when:
        service.replaceDraftOnlySound(
            opusId,
            profileId,
            attachmentId,
            replacement,
            "mp3",
            "mp3"
        )

        then:
        File updated = service.getAttachment(
            opusId, profileId, attachmentId, "mp3"
        )

        updated != null
        updated.text == "updated MP3 recording"
        updated.absolutePath == original.absolutePath

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

    def "replaceDraftOnlySound preserves original recording when upload fails"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "draft-sound-1"

        File original = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        original.parentFile.mkdirs()
        original.text = "original MP3 recording"

        MultipartFile replacement = Stub(MultipartFile) {
            transferTo(_ as File) >> { throw new IOException("Simulated upload failure") }
        }

        when:
        service.replaceDraftOnlySound(
            opusId,
            profileId,
            attachmentId,
            replacement,
            "mp3",
            "wav"
        )

        then:
        thrown(IOException)

        original.exists()
        original.text == "original MP3 recording"

        and:
        service.getAttachment(opusId, profileId, attachmentId, "wav") == null

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

    def "publishDraftSoundReplacement replaces the published file while preserving its backup"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "sound-1"

        File published = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        published.parentFile.mkdirs()
        published.text = "original recording"

        MockMultipartFile replacement = new MockMultipartFile(
            "file",
            "replacement.mp3",
            "audio/mpeg",
            "replacement recording".bytes
        )

        service.saveDraftSoundReplacement(
            opusId, profileId, attachmentId, replacement, "mp3"
        )

        when:
        File backup = service.backupPublishedSound(
            opusId, profileId, attachmentId, "mp3"
        )

        service.publishDraftSoundReplacement(
            opusId, profileId, attachmentId, "mp3"
        )

        then:
        published.text == "replacement recording"

        and:
        backup.exists()
        backup.text == "original recording"

        and:
        File staged = service.getDraftSoundReplacement(
            opusId, profileId, attachmentId, "mp3"
        )
        staged != null
        staged.text == "replacement recording"

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

    def "publishDraftSoundReplacement supports changing from MP3 to WAV"() {
        given:
        File attachmentsDirectory = Files.createTempDirectory("profile-sound-test-").toFile()
        service.grailsApplication.config.attachments.directory = attachmentsDirectory.absolutePath

        String opusId = "opus-1"
        String profileId = "profile-1"
        String attachmentId = "sound-1"

        File original = new File(
            service.getPath(opusId, profileId, attachmentId, "mp3")
        )
        original.parentFile.mkdirs()
        original.text = "original MP3 recording"

        MockMultipartFile replacement = new MockMultipartFile(
            "file",
            "replacement.wav",
            "audio/wav",
            "replacement WAV recording".bytes
        )

        service.saveDraftSoundReplacement(
            opusId, profileId, attachmentId, replacement, "wav"
        )

        when:
        service.publishDraftSoundReplacement(
            opusId, profileId, attachmentId, "wav"
        )

        then:
        File published = service.getAttachment(
            opusId, profileId, attachmentId, "wav"
        )

        published != null
        published.text == "replacement WAV recording"

        and:
        original.exists()
        original.text == "original MP3 recording"

        and:
        service.getDraftSoundReplacement(
            opusId, profileId, attachmentId, "wav"
        ) != null

        cleanup:
        attachmentsDirectory?.deleteDir()
    }

}
