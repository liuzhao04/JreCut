
public class Main {
	public static void main(String[] args) {
		System.out.println("Hello world");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Hello world");
	}
}
