/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.data.repositories.trendingshows

import app.tivi.data.repositories.shows.LocalShowStore
import app.tivi.data.repositories.shows.ShowRepository
import app.tivi.extensions.parallelForEach
import javax.inject.Inject

class TrendingShowsRepository @Inject constructor(
    private val localStore: LocalTrendingShowsStore,
    private val showStore: LocalShowStore,
    private val traktDataSource: TraktTrendingShowsDataSource,
    private val showRepository: ShowRepository
) {
    fun observeForPaging() = localStore.observeForPaging()

    fun observeForFlowable() = localStore.observeForFlowable(10, 0)

    suspend fun loadNextPage() {
        val lastPage = localStore.getLastPage()
        if (lastPage != null) updateTrendingShows(lastPage + 1, false) else refresh()
    }

    suspend fun refresh() {
        updateTrendingShows(0, true)
    }

    private suspend fun updateTrendingShows(page: Int, resetOnSave: Boolean) {
        traktDataSource.getTrendingShows(page, 20)
                .map {
                    // Grab the show id if it exists, or save the show and use it's generated ID
                    val showId = showStore.getIdForTraktId(it.show.traktId!!) ?: showStore.saveShow(it.show)
                    // Make a copy of the entry with the id
                    it.entry!!.copy(showId = showId)
                }
                .also {
                    if (resetOnSave) {
                        localStore.deleteAll()
                    }
                    // Save the related entries
                    localStore.saveTrendingShowsPage(page, it)
                    // Now update all of the related shows if needed
                    it.parallelForEach { showRepository.updateShow(it.showId) }
                }
    }
}