package ai.apiforge;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="analyses") public class Analysis {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false) public Instant createdAt;
 @Column(nullable=false,columnDefinition="text") public String result;
 protected Analysis() {}
 public Analysis(String result) { this.createdAt=Instant.now(); this.result=result; }
}
