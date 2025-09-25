import java.lang.ref.Cleaner;
import java.util.*;
import java.sql.*;

// ===== Abstract base =====
abstract class LibraryItem {
    protected String title;
    protected String author;

    public LibraryItem(String title, String author) {
        this.title = title;
        this.author = author;
    }

    public String getTitle() { return title; }
    public String getAuthor() { return author; }

    public abstract void displayInfo();
}

// ===== Book (uses Cleaner instead of finalize) =====
class Book extends LibraryItem {
    private int bookId;
    private boolean isIssued;
    private static int totalBooks = 0;

    // Cleaner to run cleanup code when Book is GC'd
    private static final Cleaner cleaner = Cleaner.create();
    private final Cleaner.Cleanable cleanable;

    private static class State implements Runnable {
        private final int id;
        State(int id) { this.id = id; }
        @Override
        public void run() {
            System.out.println("Book with ID " + id + " cleaned by Cleaner");
        }
    }

    public Book(int bookId, String title, String author) {
        super(title, author);
        this.bookId = bookId;
        this.isIssued = false;
        totalBooks++;
        // register cleanup action
        this.cleanable = cleaner.register(this, new State(this.bookId));
    }

    public int getBookId() { return bookId; }
    public boolean isIssued() { return isIssued; }
    public void issueBook() { this.isIssued = true; }
    public void returnBook() { this.isIssued = false; }

    @Override
    public void displayInfo() {
        System.out.println("[BOOK] ID: " + bookId + ", Title: " + title + ", Author: " + author + ", Issued: " + isIssued);
    }

    public static int getTotalBooks() { return totalBooks; }
}

// ===== Member =====
class Member {
    private int memberId;
    private String name;

    public Member(int memberId, String name) {
        this.memberId = memberId;
        this.name = name;
    }

    public void displayMember() {
        System.out.println("[MEMBER] ID: " + memberId + ", Name: " + name);
    }
}

// ===== Custom Exception =====
class BookNotFoundException extends Exception {
    public BookNotFoundException(String msg) { super(msg); }
}

// ===== Main program =====
public class Main {
    private static Map<Integer, Book> library = new HashMap<>();

    // ---- JDBC helper (adjust URL/user/pass as needed) ----
    private static Connection getConnection() throws Exception {
        // Ensure MySQL JDBC driver is on the classpath (mysql-connector-java)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            System.out.println("MySQL JDBC Driver not found. Add the connector jar to the classpath.\n");
            throw ex;
        }
        String url = "jdbc:mysql://localhost:3306/library?serverTimezone=UTC";
        String user = "root";
        String pass = "password";
        return DriverManager.getConnection(url, user, pass);
    }

    // Create table if not exists
    public static void createTable() {
        String q = "CREATE TABLE IF NOT EXISTS books (" +
                   "id INT PRIMARY KEY, " +
                   "title VARCHAR(200), " +
                   "author VARCHAR(200), " +
                   "issued BOOLEAN)";
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.execute(q);
            System.out.println("Table 'books' ensured in DB.");
        } catch (Exception e) {
            System.out.println("DB Error (createTable): " + e.getMessage());
        }
    }

    // Add book in-memory
    public static void addBook(Book b) {
        library.put(b.getBookId(), b);
        System.out.println("Book added successfully!");
    }

    // Save book to DB (insert)
    public static void saveBookToDB(Book b) {
        String query = "INSERT INTO books(id, title, author, issued) VALUES (?, ?, ?, ?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, b.getBookId());
            ps.setString(2, b.getTitle());
            ps.setString(3, b.getAuthor());
            ps.setBoolean(4, b.isIssued());
            ps.executeUpdate();
            System.out.println("Book saved to DB.");
        } catch (SQLIntegrityConstraintViolationException dup) {
            System.out.println("Book already exists in DB (id=" + b.getBookId() + ").");
        } catch (Exception e) {
            System.out.println("DB Error (saveBookToDB): " + e.getMessage());
        }
    }

    // Fetch and print all books from DB
    public static void fetchBooksFromDB() {
        String query = "SELECT * FROM books";
        try (Connection con = getConnection(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
            System.out.println("\nBooks in DB:");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + ", Title: " + rs.getString("title")
                    + ", Author: " + rs.getString("author") + ", Issued: " + rs.getBoolean("issued"));
            }
        } catch (Exception e) {
            System.out.println("DB Error (fetchBooksFromDB): " + e.getMessage());
        }
    }

    // Update issued status
    public static void updateBookStatus(int id, boolean issued) {
        String q = "UPDATE books SET issued = ? WHERE id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setBoolean(1, issued);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows > 0) System.out.println("Book status updated in DB.");
            else System.out.println("Book not found in DB.");
        } catch (Exception e) {
            System.out.println("DB Error (updateBookStatus): " + e.getMessage());
        }
    }

    // Delete a book
    public static void deleteBookFromDB(int id) {
        String q = "DELETE FROM books WHERE id = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows > 0) System.out.println("Book deleted from DB.");
            else System.out.println("Book not found in DB.");
        } catch (Exception e) {
            System.out.println("DB Error (deleteBookFromDB): " + e.getMessage());
        }
    }

    // Search in-memory
    public static Book searchBook(int id) throws BookNotFoundException {
        if (!library.containsKey(id)) throw new BookNotFoundException("Book with ID " + id + " not found.");
        return library.get(id);
    }

    // ---- main ----
    public static void main(String[] args) {
        // Make sure DB/table exists (if you want to use DB features)
        createTable();

        // Members
        Member m1 = new Member(1, "John");
        Member m2 = new Member(2, "Sophia");
        m1.displayMember();
        m2.displayMember();

        // Books in-memory
        Book b1 = new Book(201, "Java Programming", "James Gosling");
        Book b2 = new Book(202, "Database Systems", "C. J. Date");
        addBook(b1);
        addBook(b2);

        // Show
        b1.displayInfo();
        b2.displayInfo();

        // Save to DB (optional)
        saveBookToDB(b1);
        saveBookToDB(b2);

        // Fetch
        fetchBooksFromDB();

        // Issue a book & update DB
        b1.issueBook();
        updateBookStatus(b1.getBookId(), b1.isIssued());
        fetchBooksFromDB();

        // Delete book 202
        deleteBookFromDB(202);
        fetchBooksFromDB();

        // Try searching a non-existing book in-memory
        try {
            Book bx = searchBook(203);
            bx.displayInfo();
        } catch (BookNotFoundException e) {
            System.out.println("Exception Caught: " + e.getMessage());
        }

        System.out.println("Total Books in Library (in-memory): " + Book.getTotalBooks());

        // Encourage Cleaner to run (best-effort)
        b2 = null;
        System.gc();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
    }
}

