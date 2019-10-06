package sc2;
import java.io.*;
import java.util.*;

// custom exceptions

class UndefinedVariableException extends Exception {
	public UndefinedVariableException(String errorMessage) {
		super(errorMessage);
	}
}

class UndefinedOperationException extends Exception {
	public UndefinedOperationException(String errorMessage) {
		super(errorMessage);
	}
}

class IllegalOperationException extends Exception {
	public IllegalOperationException(String errorMessage) {
		super(errorMessage);
	}
}

class IllegalSyntaxException extends Exception {
	public IllegalSyntaxException(String errorMessage) {
		super(errorMessage);
	}
}

public class BareBonesInterpreter {
	
	public static void execProgram(Queue<Object[]> instructions, Map<String, Integer> variables) throws Exception {
		Object[] lineInfo; String instruction, line; Integer lineIndex;
		
		// poll the queue i.e. pop the first item
		while ((lineInfo = instructions.poll()) != null) {
			// fetch info about the instruction
			instruction = (String) lineInfo[0];
			line = (String) lineInfo[1];
			lineIndex = (Integer) lineInfo[2];
			
			String[] words = instruction.split(" ");
			String operation = words[0], variable = null;
			
			// check that the operation has been defined
			Set<String> definedOperations = Set.of("clear", "incr", "decr", "while", "end");
			if (!definedOperations.contains(operation))
				throw new UndefinedOperationException(
						"Undefined operation \"" + operation + "\" at line " + lineIndex + " (\"" + line + "\").");
			
			// the operation will never be end because loop ends are removed when a while operation is found
			if (!operation.contentEquals("end")) {
				// all operations except end require an argument
				try {
					variable = words[1];
				} catch (Exception e) {
					throw new IllegalSyntaxException(
							"Expected an argument after \"" + operation + "\" at line " + lineIndex + ".");
				}
				
				if (!operation.contentEquals("while")) {
					// clear, incr and decr only require one argument
					if (words.length > 2)
						throw new IllegalSyntaxException(
								"Expected one argument after \"" + operation + "\" at line " + lineIndex + ", got "
										+ (words.length - 1) + " (\"" + line + "\").");
					
					// - clear
					if (operation.contentEquals("clear"))
						variables.put(variable, 0);
					
					else if (operation.contentEquals("incr") || operation.contentEquals("decr")) {
						Integer variableValue = null;
						
						variableValue = variables.get(variable);
						if (variableValue == null)
							throw new UndefinedVariableException(
									"Access to undefined variable \"" + variable + "\" at line " + lineIndex
											+ " (\"" + line + "\").");
						
						// - incr
						if (operation.contentEquals("incr"))
							variables.put(variable, ++variableValue);
						// - decr
						else if (operation.contentEquals("decr")) {
							// variables must be non-negative integers
							if (variableValue.equals(0))
								throw new IllegalOperationException(
										"Illegal operation \"" + instruction + ";\" making \"" + variable + "\" negative at line "
												+ lineIndex + " (\"" + line + "\").");
							variables.put(variable, --variableValue);
						}
					}
				} else {
					// - while
					
					// check for proper syntax
					if (words.length != 5 || !words[2].contentEquals("not") || !words[4].contentEquals("do"))
						throw new IllegalSyntaxException("Illegal while loop syntax at line " + lineIndex + " (\"" + line + "\").");
					
					// check that the first operand variable has a value
					if (variables.get(variable) == null)
						throw new UndefinedVariableException(
								"Access to undefined variable \"" + variable + "\" at line " + lineIndex
										+ " (\"" + line + "\").");
					
					// the value to be reached can either be an integer or a stored variable
					Integer reachValue = null, refresh = 0;
					try {
						reachValue = Integer.parseInt(words[3]);
						refresh = 0;
					} catch (Exception e) {
						reachValue = variables.get(words[3]);
						if (reachValue == null)
							throw new UndefinedVariableException(
									"Access to undefined variable \"" + variable + "\" at line " + lineIndex
											+ " (\"" + line + "\").");
						// if reachValue is a stored variable, update it after each iteration
						refresh = 1;
					}
					
					// queue to hold the while body
					Queue<Object[]> whileBody = new LinkedList<Object[]>(); Object[] whileLineInfo; Integer currentWhile = 0;
					// fill the queue with the code inside the while loop, including nested loops
					while ((whileLineInfo = instructions.poll()) != null) {
						if (((String) whileLineInfo[0]).split(" ")[0].contentEquals("end") && currentWhile == 0)
							break;
						whileBody.add(whileLineInfo);
						if (((String) whileLineInfo[0]).split(" ")[0].contentEquals("while"))
							currentWhile++;
						else if (((String) whileLineInfo[0]).split(" ")[0].contentEquals("end"))
							currentWhile--;
					}
					
					// if the last line was null, the loop end is missing
					if (whileLineInfo == null)
						throw new IllegalSyntaxException("Missing while loop end after line " + lineIndex + " (\"" + line + "\").");
					
					// execute the while loop (recursively)
					while (variables.get(variable) != reachValue) {
						execProgram(new LinkedList<Object[]>(whileBody), variables);
						if (refresh == 1)
							reachValue = variables.get(words[3]);
					}
				}
			} else {
				// redundant loop end
				throw new IllegalSyntaxException("Redundant loop end at line " + lineIndex + " (\"" + line + "\").");
			}
		}
	}
	
	public static Queue<Object[]> parseProgram(BufferedReader fileReader) throws Exception {
		Queue<Object[]> instructions = new LinkedList<Object[]>(); String line; int lineIndex = 1;
		
		// read the file line by line
		while ((line = fileReader.readLine()) != null) {
			// remove leading and trailing whitespace
			line = line.trim();
			
			// lines must end with a semicolon
			if (!line.substring(line.length() - 1).contentEquals(";"))
				throw new IllegalSyntaxException("Expected a \";\" at the end of line " + lineIndex
						+ " (\"" + line + "\").");
			
			// split the line including empty strings
			String[] tempInstructions = line.split(";", -1);
			// the last string in the split line will always be empty and is supposed to be hence length - 1
			for (int i = 0; i < tempInstructions.length - 1; i++) {
				String instruction = tempInstructions[i].trim();
				
				// check for redundant semicolons
				if (instruction.isEmpty())
					throw new IllegalSyntaxException("Redundant \";\" at line " + lineIndex
							+ " (\"" + line + "\").");
				
				// add the instruction, the original line and the line index to the queue
				instructions.add(new Object[]{instruction.replaceAll("\\s+", " "), line, lineIndex});
			}
			
			lineIndex++;
		}
		
		return instructions;
	}
	
	public static void runProgram(String filename) {
		// queue for holding the parsed instructions
		Queue<Object[]> instructions = new LinkedList<Object[]>();
		
		// parse the file
		try (BufferedReader fileReader = new BufferedReader(new FileReader(filename))) {
			instructions = parseProgram(fileReader);
		} catch (Exception e) {
			System.out.println("(!)" + e.toString());
			System.exit(0);
		}
		
		// hash table for holding variables and their values
		Map<String, Integer> variablesAfterExecution = new HashMap<String, Integer>();
		
		// execute the program
		try {
			execProgram(instructions, variablesAfterExecution);
		} catch (Exception e) {
			System.out.println("(!) " + e.toString());
			System.exit(0);
		}
		
		// print variables after execution
		for (String variable : variablesAfterExecution.keySet())
			System.out.println(variable + " = " + variablesAfterExecution.get(variable));
	}
	
	public static void main(String[] args) throws Exception {
		String filename;
		
		// check for stdin args
		if (args.length > 0) {
			for (String arg : args) {
				System.out.println("Running program: " + arg);
				runProgram(arg);
			}
		}
		else {
			// read user input
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
			System.out.print("Enter a filename: ");
			filename = inputReader.readLine();
			runProgram(filename);
		}
	}
}
