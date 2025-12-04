package tachiyomix.annotations
/*
    Copyright (C) 2018 The Tachiyomi Open Source Project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * Annotation to mark a class as an IReader extension source.
 * The annotated class must:
 * - Be open or abstract
 * - Implement ireader.core.source.Source
 * - Implement ireader.core.source.DeepLinkSource if deep links are defined
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Extension
