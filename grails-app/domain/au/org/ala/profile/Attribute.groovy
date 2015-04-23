package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Attribute implements Comparable<Attribute> {

    static auditable = true

    String uuid
    Term title
    String text // = "This animal lives...."
    Attribute original // The original attribute this was copied from

    Date dateCreated
    Date lastUpdated

    static hasMany = [subAttributes: Attribute, creators: Contributor, editors: Contributor]

    static belongsTo = [profile: Profile]

    static constraints = {
        original nullable: true
    }

    static mapping = {
        subAttributes cascade: "all-delete-orphan"
    }

    def beforeValidate() {
        if (uuid == null) {
            //mint an UUID
            uuid = UUID.randomUUID().toString()
        }
    }

    int compareTo(Attribute right) {
        if (title.order == right.title.order) {
            title.name.toLowerCase() <=> right.title.name.toLowerCase()
        } else {
            title.order <=> right.title.order
        }
    }
}
