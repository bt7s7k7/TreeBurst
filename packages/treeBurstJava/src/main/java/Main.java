import java.io.IOException;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.TerminalBuilder;

import bt7s7k7.treeburst.parsing.TreeBurstParser;
import bt7s7k7.treeburst.runtime.ExpressionEvaluator;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.GlobalScope;
import bt7s7k7.treeburst.support.InputDocument;

public class Main {
	public static void main(String[] args) {
		try {
			var terminal = TerminalBuilder.builder()
					.system(true)
					.build();

			var history = new DefaultHistory();

			var reader = LineReaderBuilder.builder()
					.terminal(terminal)
					.history(history)
					.build();

			var scope = new GlobalScope();

			while (true) {
				try {
					var line = reader.readLine("> ").trim();

					if (line.isEmpty()) continue;
					if (line.equals("exit")) break;

					var inputDocument = new InputDocument("repl", line);
					var parser = new TreeBurstParser(inputDocument);
					var root = parser.parse();
					if (!parser.diagnostics.isEmpty()) {
						for (var diagnostic : parser.diagnostics) {
							System.out.println(diagnostic.format());
						}

						continue;
					}

					var result = new ExpressionResult();
					ExpressionEvaluator.evaluateExpression(root, scope, result);
					var diagnostic = result.terminate();
					if (diagnostic != null) {
						terminal.writer().println(diagnostic.format());
					}

					if (result.value != null) {
						terminal.writer().println(result.value.toString());
					}
				} catch (UserInterruptException __) {
					continue;
				} catch (EndOfFileException __) {
					break;
				}
			}

			terminal.close();
		} catch (IOException exception) {
			throw new RuntimeException("Failed to create terminal", exception);
		}
	}
}
