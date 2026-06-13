# Anahata High-ROI Ledger: Operation Deep Strike

## 📈 Distribution Funnel
| Channel | Version | Status | Downloads (Est) | Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **NB Plugin Portal (V1)** | 30.0.1 | Stable | 3,449 | Brand Awareness / Discovery |
| **NB Plugin Portal (V2)** | 1.0.0 | Stable | 105 | The Singularity / JASI Container |
| **Maven Central** | 30.0.1 | Stable | Unknown | Developer SDK Adoption |
| **GitHub Releases** | 1.1.0-SN | Beta | Unknown | Direct Binary Distribution |

 > [!TIP]
 > **One-Shot Portal Scraper:** Run this snippet via `RunningJVM.compileAndExecuteJava` to fetch the latest count.
 > ```java
 > import org.jsoup.Jsoup;
 > import org.jsoup.nodes.Document;
 > import org.jsoup.nodes.Element;
 > import java.util.concurrent.Callable;
 > import java.util.regex.Matcher;
 > import java.util.regex.Pattern;
 > 
 > public class Anahata implements Callable<String> {
 >     @Override
 >     public String call() throws Exception {
 >         StringBuilder sb = new StringBuilder();
 >         sb.append(scrape("V1", "125")).append("\n");
 >         sb.append(scrape("V2", "135"));
 >         return sb.toString();
 >     }
 > 
 >     private String scrape(String version, String id) {
 >         try {
 >             String url = "https://plugins.netbeans.apache.org/catalogue/?id=" + id;
 >             Document doc = Jsoup.connect(url).get();
 >             Element downloadIcon = doc.selectFirst("i.fa-download");
 >             if (downloadIcon != null) {
 >                 String text = downloadIcon.parent().text().trim();
 >                 Matcher m = Pattern.compile("([\\d,]+)$").matcher(text);
 >                 if (m.find()) return version + " Portal Downloads: " + m.group(1);
 >                 return version + " Found icon but could not parse count from: " + text;
 >             }
 >             return version + " Could not find download count on the page.";
 >         } catch (Exception e) {
 >             return "Error fetching " + version + ": " + e.getMessage();
 >         }
 >     }
 > }
 > ```

## 📊 Scarf Analytics (Deep Strike Intelligence)
> [!IMPORTANT]
> **Scarf API Token:** `GPf7FDFry9n4l3JHUNnrXaU9cWhLVV8KOGtF5YHKJOmDZXTPwi`

### V1 Ecosystem
- **Plugin Package ID:** `f315fd98-2ea2-42de-ad8e-f3702396d3ac`
- **SDK Package ID:** `ac594bd8-fd22-49b3-9626-88a888502a3c`
- **Status:** Sonatype integration enabled. Scarf is receiving logs from Maven Central.

### V2 Ecosystem (Singularity)
- **Parent Package ID:** `[Pending]`
- **Status:** Snapshots enabled. Releases will be tracked via Scarf templates.

## 🛠️ Milestone Log
| Date | Milestone | Token ROI | Impact |
| :--- | :--- | :--- | :--- |
| 2026-06-13 | Portal Scrape: V1=3,449, V2=105 | High | Daily ledger tracking initiated |
| 2026-02-07 | Portal Scrape: 603 DLs | High | Verified growth on Plugin Portal |
| 2026-02-06 | **Stable Release: v28.1.0** | **MAX** | First stable version of the ecosystem. |
| 2026-02-06 | CI/CD Optimization | High | Resolved duplicate deployment IDs. |
| 2026-02-06 | Scarf Integration | High | Enabled organization-level download tracking. |
| 2026-02-06 | Portal Scrape: 588 DLs | High | Verified growth on Plugin Portal |
| 2026-02-05 | V1 Release: 28.0.18 | High | UI/UX Stability & Theme Overhaul |

## ⏳ Pending Actions
- [x] **Enable Scarf in Sonatype:** Done.
- [ ] **Claim V2 Packages in Scarf:** Add `uno.anahata:anahata-asi-parent` to Scarf.
- [ ] **Automated Stats:** Implement a script to pull Scarf data into this ledger.

## 🛡️ Release Coordination Protocol
1. **Library First:** Release `gemini-java-client`.
2. **Wait for Central:** Wait 5-10 minutes for the artifact to appear in Maven Central.
3. **Verify:** Use `searchMavenIndex` to confirm availability.
4. **Plugin Second:** Trigger the `anahata-netbeans-ai` release.
5. **V2 Sync:** Ensure V2 snapshots are rolling out to Central.
