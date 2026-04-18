import com.github.ajalt.clikt.core.main
import it.toyaria.view.ToyAria

/**
 * Application entry point.
 *
 * Delegates argument parsing and command execution to [ToyAria].
 */
fun main(args: Array<String>) {
    ToyAria().main(args)
}
