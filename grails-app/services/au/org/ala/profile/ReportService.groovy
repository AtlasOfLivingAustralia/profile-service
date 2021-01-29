package au.org.ala.profile

import au.org.ala.profile.util.Utils
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters

class ReportService {

    Map draftProfiles(String opusId) {
        Opus opus = Opus.findByUuid(opusId)

        List profiles = Profile.findAllByOpusAndDraftIsNotNull(opus).collect {
            [profileId: it.uuid, scientificName: it.scientificName, draftDate: it.draft.draftDate, createdBy: it.draft.createdBy]
        }.sort { it.scientificName }

        [recordCount: profiles.size(), records: profiles]
    }

    Map archivedProfiles(String opusId) {
        Opus opus = Opus.findByUuid(opusId)

        List profiles = Profile.findAllByOpusAndArchivedDateIsNotNull(opus).collect {
            [profileId: it.uuid, scientificName: it.archivedWithName, archivedDate: it.archivedDate, archivedBy: it.archivedBy]
        }.sort { it.scientificName }

        [recordCount: profiles.size(), records: profiles]
    }

    Map mismatchedNames(String opusId, int max, int startFrom, boolean countOnly) {
        Opus opus = Opus.findByUuid(opusId)

        int count = -1
        if (max > -1) {
            count = Profile.withCriteria {
                eq "opus", opus

                or {
                    isNull "matchedName"
                    neProperty("fullName", "matchedName.fullName")
                    isNull "nslNameIdentifier"
                }

                isNull "archivedDate"
            }.size()
        }

        if (countOnly) {
            [recordCount: count > 0 ? count : 0]
        } else {
            def result = Profile.withCriteria {
                eq "opus", opus

                or {
                    isNull "matchedName"
                    neProperty("fullName", "matchedName.fullName")
                    isNull "nslNameIdentifier"
                }

                isNull "archivedDate"

                order "scientificName"

                if (max > 0) {
                    maxResults max
                    offset startFrom
                }
            }

            [recordCount: count > -1 ? count : result.size(),
             records    : result.collect {
                 [
                         profileId          : it.uuid,
                         profileName        : [scientificName: it.scientificName,
                                               fullName      : it.fullName,
                                               nameAuthor    : it.nameAuthor],
                         matchedName        : it.matchedName ? [scientificName: it.matchedName.scientificName,
                                                                fullName      : it.matchedName.fullName,
                                                                nameAuthor    : it.matchedName.nameAuthor,
                                                                guid          : it.matchedName.guid] : [:],
                         manuallyMatchedName: it.manuallyMatchedName,
                         nslNameId          : it.nslNameIdentifier
                 ]
             }]
        }
    }

    Map recentUpdates(String opusId, Date from, Date to, int max, int startFrom,
                      boolean countOnly) {
        Opus opus = Opus.findByUuid(opusId)

        int count = -1
        if (max > -1) {
            count = Profile.withCriteria {
                eq('opus', opus)
                and {
                    le('lastUpdated', to)
                    ge('lastUpdated', from)
                }
                isNull "archivedDate"

                order('lastUpdated', "desc")
            }.size()
        }

        if (countOnly) {
            [recordCount: count > 0 ? count : 0]
        } else {
            //get profiles updated or profiles with the given ids
            List profiles = Profile.withCriteria {
                eq('opus', opus)
                and {
                    le('lastUpdated', to)
                    ge('lastUpdated', from)
                }
                isNull "archivedDate"

                order('lastUpdated', "desc")

                if (max > 0) {
                    maxResults max
                    offset startFrom
                }
            }.collect {
                [profileId: it.uuid, scientificName: it.scientificName, lastUpdated: it.lastUpdated, editor: it.lastUpdatedBy]
            }

            [recordCount: count > 0 ? count : profiles.size(), records: profiles]
        }
    }

    Map recentComments(Opus opus, Date from, Date to, int max, int startFrom, boolean countOnly) {
        final profiles = Profile.withCriteria {
            eq('opus', opus)
            projections {
                property('uuid')
                property('scientificName')
            }
        }
        final profileUuids = profiles*.get(0)
        final aggOutput = Comment.collection.aggregate([
//                BaseDataAccessService.getBsonDocument([$match: [lastUpdated: [$gte: from, $lte: to], profileUuid: [$in: profileUuids]]]),
                Aggregates.match(Filters.and(Filters.in("profileUuid", profileUuids), Filters.gte("lastUpdated", from), Filters.lte("lastUpdated", to))),
                BaseDataAccessService.getBsonDocument([$sort: [lastUpdated: -1]]),
                BaseDataAccessService.getBsonDocument([$group: [
                        _id        : '$profileUuid',
                        lastUpdated: [$first: '$lastUpdated'],
                        text       : [$first: '$text'],
                        author     : [$first: '$author']
                ]]),
BaseDataAccessService.getBsonDocument([$sort: [lastUpdated: -1]])
        ])

        final results = aggOutput
        final count = results.size()

        if (countOnly) {
            return [recordCount: count]
        } else {
            final resultList = results.asList()
            final maxIndex = resultList.indices.toInt
            final records
            if (startFrom > maxIndex) {
                records = []
            } else {
                final profileMap = profiles.collectEntries { [(it[0]): it[1]] }
                final endIndex = (startFrom + max) > maxIndex ? maxIndex + 1 : (startFrom + max)
                final sizedList = max > 0 ? resultList.subList(startFrom, endIndex) : resultList
                records = sizedList.collect {
                    [
                            comment       : it.text,
                            plainComment  : Utils.cleanupText(it.text),
                            scientificName: profileMap[it['_id']],
                            lastUpdated   : it.lastUpdated,
                            editor        : it.author ? Contributor.get(it.author)?.name : ''
                    ]
                }
            }

            return [recordCount: count, records: records]
        }
    }
}
