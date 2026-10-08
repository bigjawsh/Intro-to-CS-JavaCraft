import java.io.*;
import java.net.*;
import java.util.*;

public class Chat {
    public static void main(String[] args) {
        try {
            chatSession();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    // This version allows Chat.java to still run by itself.
    public static void chatSession() {
        Scanner inputFromUser = new Scanner(System.in);
        chatSession(inputFromUser);
    }
    // This version allows JavaCraft to use the Scanner it already has.
    public static void chatSession(Scanner inputFromUser) {
        // A chat session starts by connecting to the server's TCP/IP socket.
        // We use the Scanner passed to us by JavaCraft to receive chat messages
        // from the local user in order to forward them to the server.
        //
        // By allocating the socket in a "try" statement, we ensure it
        // is released when control exits this procedure.
        // See:
        // https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        try (
            final var socket = new Socket("chat.bcs1110.svc.leastfixedpoint.nl", 5999);
        ) {
            // If control reaches here, the socket exists and is connected. We
            // extract an input stream, for reading messages from the server, and
            // an output stream, for sending messages to the server.
            final var inputFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            final var output = new PrintWriter(socket.getOutputStream());
            // Our chat system follows a very simple communication protocol.
            // https://en.wikipedia.org/wiki/Communication_protocol
            //
            // The first step in the protocol is for a connecting client to send
            // a "password" to access the server. It's not a big secret - it just
            // keeps opportunistic passers-by (e.g. AI crawler bots) out.
            output.println("F4EF9A36-5FCD-4D27-8A0A-FC7C77D3DBB2");
            output.flush();
            // Q. Why is this ^ flush() statement necessary?
            // It forces anything waiting in the output buffer to actually be
            // sent to the server immediately.
            // After the password, the protocol proceeds asynchronously. The server
            // sends stuff to us when it has anything for us, and we send stuff to it
            // when we have anything to say. These can happen at any time. But in Java,
            // things happen one after the other! There's no notion of asynchronous
            // event! We could introduce threads or some other construct for representing
            // concurrent activity.
            // https://en.wikipedia.org/wiki/Concurrency_(computer_science)
            //
            // But to keep it simple, THIS client program chooses to proceed in alternating
            // stages:
            //
            // 1 - "ping" the server, and print output we get back until we see the response
            //     to our "ping", indicating that there's nothing more to show just at the minute;
            // 2 - then, wait for input from the user.
            // 3 - send the message the user typed to the server, and loop back to the
            //     beginning again.
            //
            // We take special care not to send an empty line because that's what the protocol
            // uses to mean "ping"!
            while (true) {
                // 1. Ping the server.
                output.println("");
                output.flush(); // Don't forget to actually send the output through!
                // 1(b). Collect responses until we see the one that specifically indicates
                // that the server has received and processed our "ping".
                while (true) {
                    var fromServer = inputFromServer.readLine();
                    // We get null if the server disconnects.
                    if (fromServer == null) {
                        return;
                    }
                    if (fromServer.equals("+")) {
                        // Aha! The response to our ping! We're done with this round:
                        // move on to step 2.
                        break;
                    }
                    // Try to decrypt the message before displaying it.
                    String decryptedMessage = decryptMessage(fromServer);
                    if (decryptedMessage != null) {
                        // The DFA checks the normal message after we decrypt it.
                        if (isValidMessage(decryptedMessage)) {
                            System.out.println(decryptedMessage);
                        } else {
                            System.out.println("WARNING: Decrypted message has an invalid format.");
                            System.out.println(fromServer);
                        }
                    } else {
                        // If it isn't ROT13 encrypted just show it as-is with a warning lol
                        System.out.println("WARNING: Message could not be decrypted.");
                        System.out.println(fromServer);
                    }
                }
                // 2. Collect a line of input from the user.
                var messageToSend = inputFromUser.nextLine();
                // If the user types "back", leave Chat and return to JavaCraft.
                if (messageToSend.equalsIgnoreCase("back")) {
                    System.out.println("Returning to JavaCraft...");
                    return;
                }
                // 3. If it WASN'T a blank line, check it before sending.
                if (!messageToSend.equals("")) {
                    // Check whether the message follows the required pattern:
                    // @name: text
                    if (isValidMessage(messageToSend)) {
                        // Encrypt the message with ROT13 before sending it.
                        String encryptedMessage = encryptMessage(messageToSend);
                        output.println(encryptedMessage);
                        // Actually send the message immediately.
                        output.flush();
                    } else {
                        System.out.println("Message rejected.");
                        System.out.println("Correct format: @name: message");
                    }
                }
                // Q. What would happen if we DIDN'T take care not to send empty lines?
                // Hint: what does our code do if it sees two "ping" responses ("+" lines)
                // in a row?
                // 3(b). Loop back to step 1.
            }
        } catch (Throwable t) {
            // We are being very lazy here and not handling errors properly.
            // In a real program, we would want to handle errors relating to network
            // failure differently from errors relating to, say, errors we made in our
            // own program being signalled, or errors relating to collecting input from
            // the user.
            t.printStackTrace();
        }
    }

    // ROT13 moves every letter 13 places through the alphabet.
    // Doing ROT13 twice gives us the original message again.
    public static String rot13(String message) {
        String result = "";
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current >= 'a' && current <= 'z') {
                current = (char) ('a' + (current - 'a' + 13) % 26);
            } else if (current >= 'A' && current <= 'Z') {
                current = (char) ('A' + (current - 'A' + 13) % 26);
            }
            result = result + current;
        }
        return result;
    }

    // Cipher number 3 is ROT13 according to the lab.
    public static String encryptMessage(String message) {
        return "3," + rot13(message);
    }

    // Remove the cipher number and run ROT13 again to decrypt.
    public static String decryptMessage(String message) {
        if (!message.startsWith("3,")) {
            return null;
        }
        String encryptedMessage = message.substring(2);
        return rot13(encryptedMessage);
    }

    public static boolean isValidMessage(String message) {
        int state = 0;
        // Go through the String one character at a time.
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            switch (state) {
                // STATE 0:
                // The first character MUST be @
                case 0:
                    if (current == '@') {
                        state = 1;
                    } else {
                        return false;
                    }
                    break;
                // STATE 1:
                // There must be at least one character in the name.
                case 1:
                    if (current == ':') {
                        return false;
                    } else {
                        state = 2;
                    }
                    break;
                // STATE 2:
                // We are currently reading the name up until we find a ":"
                case 2:
                    if (current == ':') {
                        state = 3;
                    }
                    break;
                // STATE 3:
                // Immediately after ":" there MUST be a space to divide the name from the message
                case 3:
                    if (current == ' ') {
                        state = 4;
                    } else {
                        return false;
                    }
                    break;
                // STATE 4:
                // There must be at least one character in the actual message to prevent empty messages
                case 4:
                    state = 5;
                    break;
                // STATE 5:
                // We are now inside the message, any other inputed characters are accepted so we dont need to do anything else
                case 5:
                    break;
                default:
                    return false;
            }
        }
        // The message is valid ONLY if we ended in State 5 :)
        return state == 5;
    }
}