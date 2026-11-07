package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.AddCartItemRequest;
import com.sskings.ecommerce.dto.request.CheckoutRequest;
import com.sskings.ecommerce.dto.request.RegisterRequest;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.Address;
import com.sskings.ecommerce.model.CartItem;
import com.sskings.ecommerce.model.Product;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AddressRepository;
import com.sskings.ecommerce.repository.CartItemRepository;
import com.sskings.ecommerce.repository.ProductRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corridas achadas no teste de carga da pilha de produção (load-tests/prod-like): requisições simultâneas do mesmo
 * cliente. Banco real (H2) e transações reais: com mocks a corrida não existe.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class ConcurrencyIntegrationTest {

    static final int THREADS = 8;
    static final int ROUNDS = 20;

    @Autowired CartService cartService;
    @Autowired OrderService orderService;
    @Autowired AddressRepository addressRepository;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired CartItemRepository cartItemRepository;

    /** Roda a mesma tarefa em THREADS threads ao mesmo tempo e devolve as exceções. */
    private List<Throwable> concurrently(Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        pool.shutdown();
        return errors;
    }

    @Test
    @DisplayName("Adicionar o mesmo produto ao mesmo carrinho ao mesmo tempo soma as quantidades (antes: 500 por chave duplicada)")
    void concurrentAddSameProductSumsQuantity() throws Exception {
        Product product = productRepository.save(Product.builder().name("Caneca").price(BigDecimal.TEN)
                .stockQuantity(1_000).active(true).build());
        for (int round = 0; round < ROUNDS; round++) {
            User user = userRepository.save(User.builder().name("Cliente").email("c-" + UUID.randomUUID() + "@t.com").password("x").build());

            List<Throwable> errors = concurrently(() -> cartService.addItem(new AddCartItemRequest(product.getId(), 1), user.getId()));

            assertThat(errors).as("rodada %d", round).isEmpty();
            List<CartItem> items = cartItemRepository.findAllByUserId(user.getId());
            assertThat(items).as("uma linha por produto").hasSize(1);
            assertThat(items.get(0).getQuantity()).as("nenhuma adição perdida").isEqualTo(THREADS);
        }
    }

    /** Cliente com endereço e carrinho preenchido NA ORDEM dada (a ordem de inserção define a ordem dos itens). */
    private long[] customerWithCart(List<Product> productsInCartOrder) {
        User u = userRepository.save(User.builder().name("Cliente").email("o-" + UUID.randomUUID() + "@t.com").password("x").build());
        Address a = addressRepository.save(Address.builder().user(u).zipCode("01000-000").street("Rua A").number("1")
                .neighborhood("Centro").city("SP").state("SP").build());
        for (Product p : productsInCartOrder) {
            cartItemRepository.save(CartItem.builder().user(u).product(p).quantity(1).build());
        }
        return new long[]{u.getId(), a.getId()};
    }

    @Test
    @DisplayName("Checkout e cancelamento simultâneos de pedidos com vários produtos não entram em deadlock (antes: 500 no PostgreSQL)")
    void concurrentCheckoutAndCancelDoNotDeadlock() throws Exception {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            products.add(productRepository.save(Product.builder().name("P" + i).price(BigDecimal.TEN).stockQuantity(100_000).active(true).build()));
        }
        List<Product> descending = new ArrayList<>(products);
        java.util.Collections.reverse(descending);

        for (int round = 0; round < ROUNDS; round++) {
            // pedidos pendentes com os itens em ordem DECRESCENTE (o cancelamento devolve o estoque nessa ordem) ...
            List<long[]> toCancel = new ArrayList<>();
            for (int i = 0; i < THREADS / 2; i++) {
                long[] c = customerWithCart(descending);
                long orderId = orderService.checkout(new CheckoutRequest(c[1]), c[0]).id();
                toCancel.add(new long[]{orderId, c[0]});
            }
            // ... e clientes comprando os mesmos produtos em ordem CRESCENTE, ao mesmo tempo
            List<long[]> toBuy = new ArrayList<>();
            for (int i = 0; i < THREADS / 2; i++) toBuy.add(customerWithCart(products));

            AtomicInteger next = new AtomicInteger();
            List<Throwable> errors = concurrently(() -> {
                int i = next.getAndIncrement();
                if (i % 2 == 0) {
                    long[] o = toCancel.get(i / 2);
                    return orderService.cancelByCustomer(o[0], o[1]);
                }
                long[] c = toBuy.get(i / 2);
                return orderService.checkout(new CheckoutRequest(c[1]), c[0]);
            });

            assertThat(errors).as("rodada %d", round).isEmpty();
        }
    }

    @Test
    @DisplayName("Cadastros simultâneos com o mesmo email: só um conta é criada; os outros recebem erro tratável (nunca 500)")
    void concurrentRegisterSameEmailCreatesOneAccount() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String email = "dup-" + UUID.randomUUID() + "@t.com";
            AtomicInteger created = new AtomicInteger();

            List<Throwable> errors = concurrently(() -> {
                authService.register(new RegisterRequest("Cliente", email, "senha12345", null, true));
                return created.incrementAndGet();
            });

            assertThat(created.get()).as("rodada %d", round).isEqualTo(1);
            // "Email already in use" (BusinessException → 400) ou a restrição única do banco, que o
            // GlobalExceptionHandler responde com 409 — nenhum vira 500
            assertThat(errors).hasSize(THREADS - 1)
                    .allMatch(e -> e instanceof BusinessException || e instanceof DataIntegrityViolationException);
            assertThat(userRepository.findByEmail(email)).isPresent();
        }
    }
}
