package au.org.ala.profile.util

/*
 * Copyright (C) 2020 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 * 
 * Created by Temi on 25/11/20.
 */

import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import org.grails.datastore.mapping.model.MappingContext

class MongoDatastoreHolder {

    MongoDatastore mongo

    MappingContext context

    MongoDatastoreHolder(MappingContext context, MongoDatastore mongo) {
        this.context = context
        this.mongo = mongo
        FieldEntityAccess.clearReflectors()
    }
}