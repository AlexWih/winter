package io.jentz.winter.android.test.quotes

import io.jentz.winter.android.test.model.Quote
import io.jentz.winter.android.test.model.QuoteRepository
import io.jentz.winter.android.test.viewmodel.ViewModel
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit

class QuotesViewModel(
        repository: QuoteRepository
) : ViewModel<QuotesViewState>, Disposable {

    private sealed class Result {
        object IsLoading : Result()
        data class Quotes(val quotes: List<Quote>) : Result()
    }

    private var disposable: Disposable? = null

    private val viewStates: Flowable<QuotesViewState> = repository
            .getQuotes()
            .delay(FAKE_NETWORK_DELAY, TimeUnit.MILLISECONDS)
            .map<Result> { Result.Quotes(it) }
            .toFlowable()
            .startWith(Result.IsLoading)
            .scan(QuotesViewState()) { state, result ->
                when (result) {
                    is Result.IsLoading -> state.copy(isLoading = true)
                    is Result.Quotes -> state.copy(isLoading = false, quotes = result.quotes)
                }
            }
            .replay(1)
            .autoConnect(1) { disposable = it }

    override fun toFlowable(): Flowable<QuotesViewState> = viewStates

    override fun isDisposed(): Boolean = disposable?.isDisposed ?: false

    override fun dispose() {
        disposable?.dispose()
    }

    companion object {
        private const val FAKE_NETWORK_DELAY = 1000L
    }
}