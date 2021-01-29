package au.org.ala.profile

import grails.gorm.dirty.checking.DirtyCheck
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
@DirtyCheck
class FlorulaSettings {
    String id
    String drUid
}
