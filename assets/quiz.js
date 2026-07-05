function bindQuiz(root) {
  const answer = root.dataset.answer;
  const feedback = root.querySelector(".feedback");
  root.querySelectorAll("button[data-choice]").forEach((button) => {
    button.addEventListener("click", () => {
      const correct = button.dataset.choice === answer;
      feedback.textContent = correct ? root.dataset.ok : root.dataset.bad;
      feedback.className = "feedback " + (correct ? "ok" : "bad");
    });
  });
}

document.querySelectorAll("[data-quiz]").forEach(bindQuiz);
