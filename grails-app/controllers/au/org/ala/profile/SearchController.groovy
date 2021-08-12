package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.profile.util.ProfileSortOption
import au.org.ala.profile.util.SearchOptions
import grails.converters.JSON

class SearchController extends BaseController {
    SearchService searchService

    def search() {
        List<String> opusIds = params.opusId?.split(",") ?: []

        String term = params.term as String
        int pageSize = params.pageSize ? params.pageSize as int : -1
        int offset = params.offset ? params.offset as int : 0

        SearchOptions options = new SearchOptions()
        options.nameOnly = params.nameOnly?.toBoolean()
        options.includeArchived = params.boolean ('includeArchived', false)
        options.matchAll = params.matchAll?.toBoolean()
        options.searchAla = params.boolean ('searchAla', false)
        options.searchNsl = params.boolean ('searchNsl', false)
        options.includeNameAttributes = params.includeNameAttributes?.toBoolean()
        options.hideStubs = params.boolean ('hideStubs', true)

        render searchService.search(opusIds, term, offset, pageSize, options) as JSON
    }

    def findByScientificName() {
        if (!params.scientificName) {
            badRequest "scientificName is a required parameter. You can also optionally supply opusId (comma-separated list of opus ids), useWildcard (true/false to perform a wildcard search, default = true), max (max records to return), offset (0 based index to start from)."
        } else {
            List<String> opusIds = params.opusId?.split(",") ?: []

            boolean useWildcard = params.useWildcard ? params.useWildcard.equalsIgnoreCase("true") : false
            boolean autoCompleteScientificName = params.autoCompleteScientificName ? params.autoCompleteScientificName.equalsIgnoreCase("true") : false
            int max = params.max ? params.max as int : -1
            int startFrom = params.offset ? params.offset as int : 0
            ProfileSortOption sortBy = ProfileSortOption.byName(params.sortBy) ?: ProfileSortOption.getDefault()

            List<Map> profiles = searchService.findByScientificName(params.scientificName, opusIds, sortBy, useWildcard, max, startFrom, autoCompleteScientificName)

            response.setContentType("application/json")
            render profiles.collect {
                [
                        profileId     : it.uuid,
                        guid          : it.guid,
                        scientificName: it.scientificName,
                        nameAuthor    : it.nameAuthor,
                        fullName      : it.fullName,
                        rank          : it.rank,
                        opus          : [uuid: it.opus.uuid, title: it.opus.title, shortName: it.opus.shortName]
                ]
            } as JSON
        }
    }

    def findByClassificationNameAndRank() {
        if (!params.taxon || !params.scientificName) {
            badRequest "taxon (e.g. phylum, genus, species, etc) and scientificName are a required parameters. You can also optionally supply opusId (comma-separated list of opus ids), max (max records to return), offset (0 based index to start from), recursive (whether to get all subordinate taxa (true) or only the next rank (false) - default is true)."
        } else {
            List<String> opusIds = params.opusId?.split(",") ?: []

            boolean countChildren = params.boolean('countChildren', false)
            int max = params.int('max', -1)
            int startFrom = params.int('offset', 0)
            boolean immediateChildrenOnly = params.boolean('immediateChildrenOnly', false)
            boolean includeTaxon = params.boolean('includeTaxon', false)
            ProfileSortOption sortBy = ProfileSortOption.byName(params.sortBy) ?: ProfileSortOption.getDefault()
            List<Map> profiles = searchService.findByClassificationNameAndRank(params.taxon, params.scientificName, opusIds, sortBy, max, startFrom, immediateChildrenOnly, includeTaxon)

            response.setContentType("application/json")
            render profiles.collect { profile ->
                [
                        profileId     : profile.uuid,
                        guid          : profile.guid,
                        scientificName: profile.scientificName,
                        name          : profile.scientificName,
                        rank          : profile.rank,
                        taxonomicOrder: profile.taxonomicOrder,
                        opus          : [uuid: profile.opus.uuid, title: profile.opus.title, shortName: profile.opus.shortName],
                        childCount    : countChildren ? Profile.withCriteria {
                            eq "opus", profile.opus
                            isNull "archivedDate"
                            ne "uuid", profile.uuid

                            "classification" {
                                eq "rank", "${profile.rank.toLowerCase()}"
                                ilike "name", "${profile.scientificName}"
                            }

                            projections {
                                count()
                            }
                        }[0] : -1
                ]
            } as JSON
        }
    }

    def totalByClassificationNameAndRank() {
        if (!params.taxon || !params.scientificName) {
            badRequest "taxon (e.g. phylum, genus, species, etc) and scientificName are a required parameters. You can also optionally supply opusId (comma-separated list of opus ids), max (max records to return), offset (0 based index to start from), recursive (whether to get all subordinate taxa (true) or only the next rank (false) - default is true)."
        } else {
            List<String> opusIds = params.opusId?.split(",") ?: []

            boolean immediateChildrenOnly = params.boolean('immediateChildrenOnly', false)
            boolean includeTaxon = params.boolean('includeTaxon', false)
            int total = searchService.totalDescendantsByClassificationAndRank(params.taxon, params.scientificName, opusIds, immediateChildrenOnly, includeTaxon)

            def result = [total: total]
            respond result
        }
    }

    def findProfilesByClassificationNameAndRankAndGetTotalProfilesCount() {
        if ((!params.taxon || !params.scientificName) && !params.rankFilter ) {
            badRequest "taxon (e.g. phylum, genus, species, etc) and scientificName, or rankFilter are required parameters . You can also optionally supply opusId (comma-separated list of opus ids), max (max records to return), offset (0 based index to start from), recursive (whether to get all subordinate taxa (true) or only the next rank (false) - default is true)."
        } else {
            List<String> opusIds = params.opusId?.split(",") ?: []

            int max = params.int('max', -1)
            int startFrom = params.int('offset', 0)
            boolean immediateChildrenOnly = params.boolean('immediateChildrenOnly', false)
            boolean includeTaxon = params.boolean('includeTaxon', false)
            ProfileSortOption sortBy = ProfileSortOption.byName(params.sortBy) ?: ProfileSortOption.getDefault()
            List<Map> profiles = searchService.findByClassificationNameAndRank(params.taxon, params.scientificName, opusIds, sortBy, max, startFrom, immediateChildrenOnly, includeTaxon, params.rankFilter)
            profiles = profileService.trimProfiles(profiles)
            Integer total = searchService.totalDescendantsByClassificationAndRank(params.taxon, params.scientificName, opusIds, immediateChildrenOnly, includeTaxon, params.rankFilter)
            response.setContentType("application/json")
            render ([profiles: profiles, count: total] as JSON)
        }
    }

    def getRanks() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            Map<String, Integer> result = searchService.getRanks(opus?.uuid)
            render result as JSON
        }
    }

    def groupByRank() {
        if (!params.opusId || !params.taxon) {
            badRequest "opusId and taxon are required parameters. You can also optionally supply filter (name to filter on), max (max records to return) and offset (0 based index to start from)."
        } else {
            int max = params.max ? params.max as int : -1
            int startFrom = params.offset ? params.offset as int : 0

            Opus opus = getOpus()

            Map<String, Integer> result = searchService.groupByRank(opus?.uuid, params.taxon, params.filter, max, startFrom)

            render result as JSON
        }
    }

    def getImmediateChildren() {
        if (!params.opusId || !params.rank || !params.name) {
            badRequest "opusId, rank and name are required parameters. max, offset and filter are optional."
        } else {
            Opus opus = getOpus()
            int max = params.max ? params.max as int : -1
            int startFrom = params.offset ? params.offset as int : 0
            String filter = params.filter ?: null

            List children = searchService.getImmediateChildren(opus, params.rank, params.name, filter, max, startFrom)
            List enprofiledChildren = searchService.findProfilesForImmediateChildren(opus, children)
            response.setContentType("application/json")
            render enprofiledChildren as JSON
        }
    }

    @RequireApiKey
    def reindex() {

       searchService.reindexAll()

       render ((Status.first()?:[:]) as JSON)
    }
}
