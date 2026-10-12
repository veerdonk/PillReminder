package nl.veerdonk.pillreminder.data

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PillStockRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newRepo() = DataStorePillStockRepository(
        DataStorePillStockRepository.createDataStore(
            file = { tmp.root.resolve("pill_stock.preferences_pb") },
            scope = backgroundScope,
        ),
    )

    @Test
    fun emptyStoreIsNotCounting() = runTest {
        assertNull(newRepo().current())
    }

    @Test
    fun setTakeAndStop() = runTest {
        val repo = newRepo()
        repo.set(90)
        repo.take(1)
        assertEquals(89, repo.current())
        repo.take(100)
        assertEquals(0, repo.current())
        repo.set(null)
        assertNull(repo.current())
    }

    @Test
    fun takeWhileNotCountingDoesNothing() = runTest {
        val repo = newRepo()
        repo.take(1)
        assertNull(repo.current())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCount() = runTest {
        newRepo().set(-1)
    }
}
