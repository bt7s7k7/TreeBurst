import java.io.IOException;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.TerminalBuilder;

import bt7s7k7.treeburst.parsing.TreeBurstParser;
import bt7s7k7.treeburst.runtime.ExpressionResult;
import bt7s7k7.treeburst.runtime.Realm;
import bt7s7k7.treeburst.support.InputDocument;

public class Main {
	public static void main(String[] args) {
		try {
			var terminal = TerminalBuilder.builder()
					.system(true)
					.build();

			var history = new DefaultHistory();

			var reader = LineReaderBuilder.builder()
					.option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
					.terminal(terminal)
					.history(history)
					.build();

			var realm = new Realm();
			var scope = realm.globalScope;

			var dumpBytecode = false;
			var dumpAST = false;

			while (true) {
				try {
					var line = reader.readLine("> ").trim();

					if (line.isEmpty()) continue;
					if (line.equals("exit")) break;

					while (line.startsWith(".")) {
						if (line.startsWith(".byte")) {
							dumpBytecode = !dumpBytecode;
							terminal.writer().println("Dump bytecode: " + dumpBytecode);
							line = line.substring(5);
							continue;
						}

						if (line.equals(".ast")) {
							dumpAST = !dumpAST;
							terminal.writer().println("Dump AST: " + dumpAST);
							line = line.substring(4);
							continue;
						}

						terminal.writer().println("Invalid REPL command");
						break;
					}

					var inputDocument = new InputDocument("repl", line);
					var parser = new TreeBurstParser(inputDocument);
					var root = parser.parse();

					if (dumpAST) {
						terminal.writer().println(root.getExpression().toFormattedString());
					}

					if (!parser.diagnostics.isEmpty()) {
						for (var diagnostic : parser.diagnostics) {
							terminal.writer().println(diagnostic.format());
						}

						continue;
					}

					var result = new ExpressionResult();

					if (dumpBytecode) {
						root.compile(scope, result);
						terminal.writer().println(root.toString());
					}

					// If an error was generated during dumping bytecode, do not evaluate
					if (result.label == null) {
						root.evaluate(scope, result);
					}

					var diagnostic = result.terminate();
					if (diagnostic != null) {
						terminal.writer().println(diagnostic.format());
					}

					if (result.value != null) {
						terminal.writer().println(realm.inspect(result.value));
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
