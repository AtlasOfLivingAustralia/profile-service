package au.org.ala.profile

import au.org.ala.profile.util.Utils
import org.springframework.web.multipart.MultipartFile

class AttachmentService {

    static final long DEFAULT_MAX_SOUND_FILE_SIZE = 5_000_000L
    static final Map<String, Set<String>> SOUND_CONTENT_TYPES = [
            mp3: ['audio/mpeg', 'audio/mp3', 'audio/x-mp3'] as Set,
            wav: ['audio/wav', 'audio/x-wav', 'audio/wave', 'audio/vnd.wave'] as Set
    ]

    def grailsApplication

    boolean deleteAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.delete()
    }

    void saveAttachment(String opusId, String profileId, String attachmentId, MultipartFile incomingFile, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))
        file.mkdirs()
        incomingFile.transferTo(file)
    }

    File getAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.exists() ? file : null
    }

    String getPath(String opusId, String profileId, String attachmentId, String extension) {
        "${grailsApplication.config.attachments.directory}/${opusId}/${profileId ? profileId + '/' : ''}${attachmentId}.${extension}"
    }

    String getDraftSoundPath(String opusId, String profileId, String attachmentId, String extension) {
        "${grailsApplication.config.attachments.directory}/${opusId}/${profileId}/draft/${attachmentId}.${extension}"
    }

    void saveDraftSoundReplacement(String opusId, String profileId, String attachmentId,
                                   MultipartFile incomingFile, String extension) {
        File destination = new File(getDraftSoundPath(opusId, profileId, attachmentId, extension))
        destination.parentFile.mkdirs()

        File temporary = File.createTempFile("sound-replacement-", ".tmp", destination.parentFile)

        try {
            incomingFile.transferTo(temporary)

            java.nio.file.Files.move(
                temporary.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    File getDraftSoundReplacement(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getDraftSoundPath(opusId, profileId, attachmentId, extension))
        file.exists() ? file : null
    }

    void deleteDraftSoundReplacements(String opusId, String profileId, String attachmentId, String exceptExtension = null) {
        SOUND_CONTENT_TYPES.keySet().each { String extension ->
            if (extension != exceptExtension) {
                File file = new File(getDraftSoundPath(opusId, profileId, attachmentId, extension))
                if (file.exists() && !file.delete()) {
                    throw new IOException("Failed to delete staged sound replacement")
                }
            }
        }
    }

    void publishDraftSoundReplacement(String opusId, String profileId, String attachmentId, String extension) {
        File staged = getDraftSoundReplacement(opusId, profileId, attachmentId, extension)

        if (!staged) {
            throw new IllegalStateException("Staged sound replacement not found")
        }

        File destination = new File(getPath(opusId, profileId, attachmentId, extension))
        destination.parentFile.mkdirs()

        File temporary = File.createTempFile("sound-publish-", ".tmp", destination.parentFile)

        try {
            java.nio.file.Files.copy(
                staged.toPath(),
                temporary.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )

            java.nio.file.Files.move(
                temporary.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    File backupPublishedSound(String opusId, String profileId,
                              String attachmentId, String extension) {
        File original = getAttachment(opusId, profileId, attachmentId, extension)

        if (!original) {
            throw new IllegalStateException("Published sound file not found")
        }

        File backup = File.createTempFile(
            "sound-backup-",
            ".${extension}",
            original.parentFile
        )

        try {
            java.nio.file.Files.copy(
                original.toPath(),
                backup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return backup
        } catch (Exception e) {
            backup.delete()
            throw e
        }
    }

    Map validateSound(MultipartFile incomingFile) {
        if (!incomingFile || incomingFile.empty) {
            return [valid: false, error: 'A sound file is required']
        }

        String extension = Utils.getFileExtension(incomingFile.originalFilename)?.toLowerCase()
        if (!SOUND_CONTENT_TYPES.containsKey(extension)) {
            return [valid: false, error: 'Invalid sound file extension - must be one of [mp3, wav]']
        }

        long maxFileSize = grailsApplication.config.getProperty('attachments.sound.maxFileSize', Long, DEFAULT_MAX_SOUND_FILE_SIZE)
        if (incomingFile.size > maxFileSize) {
            return [valid: false, error: "Sound file exceeds the maximum size of ${maxFileSize} bytes"]
        }

        String contentType = incomingFile.contentType?.toLowerCase()?.split(';')?.first()?.trim()
        if (!SOUND_CONTENT_TYPES[extension].contains(contentType)) {
            return [valid: false, error: "Invalid content type '${incomingFile.contentType}' for a ${extension} sound file"]
        }

        [valid: true, extension: extension, contentType: contentType]
    }

    Map<String,File> collectAllAttachmentsIncludingOriginalNames(Profile profile) {
        Map<String,File> fileMap = [:]
        if (profile.attachments) {
            List<Attachment> attachments = profile.getAttachments()
            attachments.each { attachment ->
                File file = getAttachment(profile.opus.uuid, profile.uuid, attachment.uuid, Utils.getFileExtension(attachment.filename))
                if (file) {
                    fileMap.put(attachment.getFilename(),file)
                }
            }
        }
        return fileMap
    }


}
