import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.databind.ObjectMapper;

// Класс CrptApi с поддержкой ограничения количества запросов
public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final Lock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Обнуляем семафор через указанный интервал времени
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } finally {
                lock.unlock();
            }
        }, 0, 1, timeUnit);
    }

    // Метод создания документа для ввода в оборот товара
    public void createDocumentForGoods(Document document, String signature) throws IOException, InterruptedException {
        // Ожидание доступности запроса
        semaphore.acquire();

        try {
            // Реализация POST запроса
            URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Signature", signature);
            connection.setDoOutput(true);

            // Преобразование объекта Document в JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonInputString = objectMapper.writeValueAsString(document);

            // Отправка JSON данных
            connection.getOutputStream().write(jsonInputString.getBytes());

            // Проверка ответа от сервера
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Документ успешно отправлен.");
            } else {
                System.out.println("Ошибка при отправке документа: " + responseCode);
            }

        } finally {
            // Всегда освобождаем семафор после завершения запроса
            semaphore.release();
        }
    }

    // Внутренний класс для документа
    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        // Внутренний класс для описания Description
        public static class Description {
            public String participantInn;
        }

        // Внутренний класс для товара Product
        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
}
