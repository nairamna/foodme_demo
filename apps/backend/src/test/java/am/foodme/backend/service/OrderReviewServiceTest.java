package am.foodme.backend.service;

import am.foodme.backend.dto.OrderDto;
import am.foodme.backend.dto.OrderReviewRequestDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Chef;
import am.foodme.backend.model.Customer;
import am.foodme.backend.model.Order;
import am.foodme.backend.model.OrderReview;
import am.foodme.backend.repository.ChefRepository;
import am.foodme.backend.repository.OrderRepository;
import am.foodme.backend.repository.OrderReviewRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-9: who can rate which order, and how the chef's rating is recalculated. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderReviewServiceTest {

    private static final String OWNER = "ann@example.com";

    @Mock private OrderRepository orderRepository;
    @Mock private OrderReviewRepository orderReviewRepository;
    @Mock private ChefRepository chefRepository;

    private SimpleMeterRegistry meterRegistry;
    private OrderReviewService service;
    private Order stored;
    private Chef chef;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderReviewService(orderRepository, orderReviewRepository, chefRepository, meterRegistry);

        Customer owner = new Customer();
        owner.setId(1L);
        owner.setEmail(OWNER);
        chef = new Chef();
        chef.setId(2L);
        chef.setRating(4.8);
        stored = new Order();
        stored.setId(7L);
        stored.setNumber("FM-100007");
        stored.setStatus("DELIVERED");
        stored.setCustomer(owner);
        stored.setChef(chef);

        when(orderRepository.findByNumber("FM-100007")).thenAnswer(inv -> Optional.of(stored));
        when(orderRepository.findByNumber("FM-404")).thenReturn(Optional.empty());
        when(orderReviewRepository.existsByOrderId(7L)).thenReturn(false);
        when(orderReviewRepository.saveAndFlush(any(OrderReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReviewRepository.averageRatingForChef(2L)).thenReturn(4.0);
    }

    private static OrderReviewRequestDto request(int rating, String comment) {
        return new OrderReviewRequestDto(BigDecimal.valueOf(rating), comment);
    }

    @Test
    void review_ownDeliveredOrder_savesRatingForOrderCustomerAndChef() {
        OrderDto result = service.reviewForCustomer("FM-100007", OWNER, request(4, "  Tasty  "));

        ArgumentCaptor<OrderReview> saved = ArgumentCaptor.forClass(OrderReview.class);
        verify(orderReviewRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrder()).isSameAs(stored);
        assertThat(saved.getValue().getCustomer()).isSameAs(stored.getCustomer());
        assertThat(saved.getValue().getChef()).isSameAs(chef);
        assertThat(saved.getValue().getRating()).isEqualTo(4);
        assertThat(saved.getValue().getComment()).isEqualTo("Tasty");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();

        assertThat(result.getReview().getRating()).isEqualTo(4);
        assertThat(result.getReview().getComment()).isEqualTo("Tasty");
        assertThat(meterRegistry.counter("foodme.orders.reviewed").count()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void review_blankComment_isStoredAsNoComment(String comment) {
        OrderDto result = service.reviewForCustomer("FM-100007", OWNER, request(5, comment));

        assertThat(result.getReview().getComment()).isNull();
    }

    @Test
    void review_emailWithDifferentCaseAndSpaces_stillMatchesOwner() {
        OrderDto result = service.reviewForCustomer("FM-100007", "  Ann@Example.COM ", request(5, null));

        assertThat(result.getReview().getRating()).isEqualTo(5);
    }

    @Test
    void review_setsChefRatingToRoundedAverage() {
        when(orderReviewRepository.averageRatingForChef(2L)).thenReturn(13.0 / 3);

        service.reviewForCustomer("FM-100007", OWNER, request(4, null));

        assertThat(chef.getRating()).isEqualTo(4.3);
        verify(chefRepository).save(chef);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"4.25, 4.3", "4.24, 4.2", "5.0, 5.0", "1.0, 1.0", "3.95, 4.0"})
    void roundToOneDecimal_roundsHalfUp(double average, double expected) {
        assertThat(OrderReviewService.roundToOneDecimal(average)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "ACCEPTED", "REJECTED", "CANCELLED"})
    void review_orderNotDelivered_isRefusedAndNothingSaved(String status) {
        stored.setStatus(status);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.reviewForCustomer("FM-100007", OWNER, request(5, null)));

        assertThat(ex.getMessage()).isEqualTo("Only delivered orders can be reviewed.");
        verify(orderReviewRepository, never()).saveAndFlush(any());
        assertThat(chef.getRating()).isEqualTo(4.8);
    }

    @Test
    void review_alreadyReviewed_isRefused() {
        when(orderReviewRepository.existsByOrderId(7L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.reviewForCustomer("FM-100007", OWNER, request(5, null)));

        assertThat(ex.getMessage()).isEqualTo("Order already reviewed.");
        verify(orderReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void review_concurrentSecondSubmission_isRefusedAsAlreadyReviewed() {
        when(orderReviewRepository.saveAndFlush(any(OrderReview.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate order_id"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.reviewForCustomer("FM-100007", OWNER, request(5, null)));

        assertThat(ex.getMessage()).isEqualTo("Order already reviewed.");
        assertThat(chef.getRating()).isEqualTo(4.8);
    }

    @Test
    void review_someoneElsesOrder_looksNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.reviewForCustomer("FM-100007", "mallory@example.com", request(5, null)));

        assertThat(ex.getMessage()).isEqualTo("Order FM-100007 not found");
        verify(orderReviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void review_orderWithoutCustomer_looksNotFound() {
        stored.setCustomer(null);

        assertThrows(NotFoundException.class,
                () -> service.reviewForCustomer("FM-100007", OWNER, request(5, null)));
    }

    @Test
    void review_unknownNumber_throwsNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.reviewForCustomer("FM-404", OWNER, request(5, null)));

        assertThat(ex.getMessage()).isEqualTo("Order FM-404 not found");
    }
}
